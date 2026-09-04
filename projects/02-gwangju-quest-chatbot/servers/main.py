"""Gwangju Quest FastAPI server."""

import json
import os
import re
import shutil
import uuid
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any, Literal

from dotenv import load_dotenv
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from langchain_chroma import Chroma
from langchain_community.document_loaders import PyMuPDFLoader, TextLoader
from langchain_core.documents import Document
from langchain_openai import ChatOpenAI, OpenAIEmbeddings
from langchain_text_splitters import MarkdownHeaderTextSplitter, RecursiveCharacterTextSplitter
from pydantic import BaseModel, Field

from prompt import GENERAL_PROMPT, RAG_PROMPT

load_dotenv()

BASE_DIR = Path(__file__).resolve().parent
DATA_DIR = BASE_DIR / "data"
CHROMA_DB_PATH = BASE_DIR / "chroma_data"
DATA_DIR.mkdir(parents=True, exist_ok=True)

API_KEY = os.getenv("OPENAI_API_KEY") or os.getenv("API_KEY")
BASE_URL = os.getenv("OPENAI_BASE_URL") or os.getenv("base_url") or None
GPT_MODEL = os.getenv("GPT_MODEL", "gpt-5-mini")
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL_NAME") or os.getenv(
    "EMBEDDING_MODEL", "text-embedding-3-small"
)

CHUNK_SIZE = 450
CHUNK_OVERLAP = 60
RAG_SCORE_THRESHOLD = 0.20
RAG_TOP_K = 5

llm: ChatOpenAI | None = None
embeddings: OpenAIEmbeddings | None = None
vectorstore: Chroma | None = None
startup_warning: str | None = None


class ChatTurn(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=4000)


class ProfileUpdates(BaseModel):
    companionType: Literal["친구", "연인", "가족", "혼자"] | None = None
    duration: str | None = Field(default=None, max_length=40)
    transport: str | None = Field(default=None, max_length=40)
    preferences: list[Literal["카페", "맛집", "사진", "문화", "자연", "산책", "체험"]] = Field(
        default_factory=list, max_length=7
    )
    walkingPreference: str | None = Field(default=None, max_length=40)
    weather: str | None = Field(default=None, max_length=40)
    currentArea: str | None = Field(default=None, max_length=60)


class QuestPayload(BaseModel):
    id: str | None = Field(default=None, max_length=100)
    title: str = Field(min_length=1, max_length=80)
    description: str = Field(min_length=1, max_length=500)
    difficulty: Literal["EASY", "NORMAL", "HARD", "SECRET"] = "NORMAL"
    xp: Literal[50, 100, 150, 200] = 100
    location: str | None = Field(default=None, max_length=80)
    status: Literal["active", "completed"] = "active"


class StructuredAIResponse(BaseModel):
    response: str = Field(min_length=1)
    profile_updates: ProfileUpdates = Field(default_factory=ProfileUpdates)
    quest: QuestPayload | None = None
    intent: Literal["NONE", "QUEST_COMPLETE", "TRIP_END"] = "NONE"


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=4000)
    use_rag: bool = True
    game_state: dict[str, Any] | None = None
    history: list[ChatTurn] = Field(default_factory=list, max_length=12)
    profile: dict[str, Any] | None = None
    completed_quests: list[dict[str, Any]] = Field(default_factory=list, max_length=30)


def extract_documents(file_path: Path) -> list[Document]:
    """Read an uploaded txt, md, or pdf file as LangChain documents."""
    loader = (
        PyMuPDFLoader(str(file_path))
        if file_path.suffix.lower() == ".pdf"
        else TextLoader(str(file_path), encoding="utf-8")
    )
    documents = loader.load()
    for document in documents:
        document.metadata["source"] = file_path.name
    return documents


def chunk_documents(documents: list[Document]) -> list[Document]:
    """Preserve Markdown sections, then split long sections with overlap."""
    body_splitter = RecursiveCharacterTextSplitter(
        chunk_size=CHUNK_SIZE,
        chunk_overlap=CHUNK_OVERLAP,
        separators=["\n\n", "\n", ". ", " ", ""],
    )
    header_splitter = MarkdownHeaderTextSplitter(
        headers_to_split_on=[("#", "title"), ("##", "section"), ("###", "subsection")],
        strip_headers=False,
    )
    chunks: list[Document] = []
    for document in documents:
        source = str(document.metadata.get("source", ""))
        if Path(source).suffix.lower() != ".md":
            chunks.extend(body_splitter.split_documents([document]))
            continue
        sections = header_splitter.split_text(document.page_content)
        for section in sections:
            section.metadata.update(document.metadata)
        chunks.extend(body_splitter.split_documents(sections))
    return chunks


def index_document(file_path: Path) -> int:
    if vectorstore is None:
        raise RuntimeError("벡터 데이터베이스가 준비되지 않았습니다.")
    chunks = chunk_documents(extract_documents(file_path))
    if not chunks:
        return 0
    ids = [f"{file_path.name}:{uuid.uuid4()}" for _ in chunks]
    vectorstore.add_documents(chunks, ids=ids)
    return len(chunks)


def index_seed_documents() -> None:
    """Index bundled Gwangju guides once for a fresh collection."""
    if vectorstore is None or vectorstore.get(limit=1).get("ids"):
        return
    for file_path in sorted(DATA_DIR.glob("gwangju_*.md")):
        index_document(file_path)


def format_game_state(game_state: dict[str, Any] | None) -> str:
    if not game_state:
        return "아직 게임 상태 정보가 없습니다."
    players = ", ".join(str(name) for name in game_state.get("players", [])) or "미입력"
    return (
        f"플레이어: {players}\n"
        f"레벨: {game_state.get('level', 1)}\n"
        f"XP: {game_state.get('xp', 0)}\n"
        f"완료 퀘스트: {game_state.get('completedQuestCount', 0)}\n"
        f"현재 퀘스트: {game_state.get('currentQuest', '없음')}\n"
        f"현재 퀘스트 출처: {game_state.get('currentQuestSource', 'unknown')}"
    )


def format_history(history: list[ChatTurn]) -> list[tuple[str, str]]:
    """Convert validated browser history to LangChain chat-message tuples."""
    role_map = {"user": "human", "assistant": "ai"}
    return [(role_map[turn.role], turn.content.strip()) for turn in history[-10:]]


def build_search_query(question: str, history: list[ChatTurn]) -> str:
    """Give short follow-up questions enough recent user context for RAG retrieval."""
    recent_user_messages = [
        turn.content.strip() for turn in history if turn.role == "user"
    ][-2:]
    if not recent_user_messages:
        return question
    return "\n".join([*recent_user_messages, question])


def format_mapping(data: dict[str, Any] | None, empty_message: str) -> str:
    if not data:
        return empty_message
    lines = []
    for key, value in data.items():
        if value is None or value == "" or value == []:
            continue
        rendered = ", ".join(str(item) for item in value) if isinstance(value, list) else str(value)
        lines.append(f"- {key}: {rendered}")
    return "\n".join(lines) or empty_message


def format_completed_quests(completed_quests: list[dict[str, Any]]) -> str:
    if not completed_quests:
        return "아직 완료한 Quest가 없습니다."
    lines = []
    for quest in completed_quests[-10:]:
        title = str(quest.get("title", "제목 없음"))
        location = str(quest.get("location") or "장소 미지정")
        lines.append(f"- {title} ({location})")
    return "\n".join(lines)


def extract_profile_updates(message: str) -> dict[str, Any]:
    """Extract explicit, stable profile facts so state updates do not depend only on JSON quality."""
    updates: dict[str, Any] = {}

    relation_patterns = [
        ("연인", ("여자친구", "남자친구", "연인", "애인", "데이트")),
        ("가족", ("부모님", "가족", "아이랑", "아이와", "아기")),
        ("혼자", ("혼자", "나 홀로", "나홀로")),
        ("친구", ("친구", "동료")),
    ]
    for relation, keywords in relation_patterns:
        if any(keyword in message for keyword in keywords):
            updates["companionType"] = relation
            break

    duration_match = re.search(r"(\d+)\s*시간", message)
    if duration_match:
        updates["duration"] = f"{duration_match.group(1)}시간"
    elif "반나절" in message:
        updates["duration"] = "반나절"
    elif any(keyword in message for keyword in ("하루 종일", "하루 여행", "당일치기")):
        updates["duration"] = "하루"

    transport_patterns = [
        ("대중교통", ("대중교통", "버스", "지하철", "도시철도")),
        ("자가용", ("자가용", "자동차", "차로", "렌터카")),
        ("도보", ("도보", "걸어서")),
        ("택시", ("택시",)),
    ]
    for transport, keywords in transport_patterns:
        if any(keyword in message for keyword in keywords):
            updates["transport"] = transport
            break

    preference_keywords = ("카페", "맛집", "사진", "문화", "자연", "산책", "체험")
    preferences = [keyword for keyword in preference_keywords if keyword in message]
    if preferences:
        updates["preferences"] = preferences

    if re.search(r"(많이|오래)\s*걷[^.?!]*(싫|힘들|어려)|걷는\s*건\s*싫", message):
        updates["walkingPreference"] = "적게 걷기"
    elif re.search(r"많이\s*걸어도\s*(괜찮|좋)|걷는\s*(걸|것을?)\s*좋아", message):
        updates["walkingPreference"] = "많이 걷기"

    weather_patterns = [
        ("비", ("비가", "비 와", "비오는", "비 오는", "우천")),
        ("더움", ("더워", "폭염", "무더위", "더운 날")),
        ("추움", ("추워", "한파", "추운 날")),
        ("선선함", ("선선", "날씨 좋아", "맑은 날")),
    ]
    for weather, keywords in weather_patterns:
        if any(keyword in message for keyword in keywords):
            updates["weather"] = weather
            break

    area_names = (
        "동명동", "양림동", "충장로", "상무지구", "광주송정역", "송정역",
        "1913송정역시장", "국립아시아문화전당", "ACC", "무등산", "광주호",
        "5·18민주광장", "문화전당역", "예술의 거리", "사직공원", "중외공원",
    )
    location_markers = ("도착", "왔어", "와 있어", "지금", "출발", "있는 중")
    if any(marker in message for marker in location_markers):
        for area in area_names:
            if area in message:
                updates["currentArea"] = "광주송정역" if area == "송정역" else area
                break

    return updates


def infer_intent(message: str) -> Literal["NONE", "QUEST_COMPLETE", "TRIP_END"]:
    normalized = re.sub(r"[\s.!?]+", "", message)
    if any(phrase in normalized for phrase in ("여행끝", "오늘은여기까지", "종료할래", "여행종료")):
        return "TRIP_END"
    if normalized in {"완료", "성공", "미션완료", "미션성공", "퀘스트끝", "퀘스트완료"}:
        return "QUEST_COMPLETE"
    if re.search(r"(미션|퀘스트).*(완료|성공|끝)|둘다.*골랐어", normalized):
        return "QUEST_COMPLETE"
    return "NONE"


def parse_structured_response(raw_text: str) -> StructuredAIResponse | None:
    candidate = raw_text.strip()
    if candidate.startswith("```"):
        candidate = re.sub(r"^```(?:json)?\s*", "", candidate, flags=re.IGNORECASE)
        candidate = re.sub(r"\s*```$", "", candidate)
    start, end = candidate.find("{"), candidate.rfind("}")
    if start < 0 or end <= start:
        return None
    try:
        data = json.loads(candidate[start : end + 1])
    except json.JSONDecodeError:
        return None
    try:
        return StructuredAIResponse.model_validate(data)
    except Exception:
        response = data.get("response") if isinstance(data, dict) else None
        if not isinstance(response, str) or not response.strip():
            return None

        try:
            profile_updates = ProfileUpdates.model_validate(data.get("profile_updates") or {})
        except Exception:
            profile_updates = ProfileUpdates()

        quest = None
        raw_quest = data.get("quest")
        if isinstance(raw_quest, dict):
            normalized_quest = dict(raw_quest)
            difficulty = str(normalized_quest.get("difficulty", "NORMAL")).upper()
            if difficulty not in {"EASY", "NORMAL", "HARD", "SECRET"}:
                difficulty = "NORMAL"
            normalized_quest["difficulty"] = difficulty
            xp_by_difficulty = {"EASY": 50, "NORMAL": 100, "HARD": 150, "SECRET": 200}
            try:
                requested_xp = int(normalized_quest.get("xp", xp_by_difficulty[difficulty]))
            except (TypeError, ValueError):
                requested_xp = xp_by_difficulty[difficulty]
            normalized_quest["xp"] = requested_xp if requested_xp in {50, 100, 150, 200} else xp_by_difficulty[difficulty]
            normalized_quest["status"] = "active"
            try:
                quest = QuestPayload.model_validate(normalized_quest)
            except Exception:
                quest = None

        intent = data.get("intent", "NONE")
        if intent not in {"NONE", "QUEST_COMPLETE", "TRIP_END"}:
            intent = "NONE"
        return StructuredAIResponse(
            response=response,
            profile_updates=profile_updates,
            quest=quest,
            intent=intent,
        )


def merge_profile_updates(ai_updates: ProfileUpdates, explicit_updates: dict[str, Any]) -> dict[str, Any]:
    merged = ai_updates.model_dump(exclude_none=True)
    merged_preferences = list(dict.fromkeys([
        *merged.get("preferences", []),
        *explicit_updates.get("preferences", []),
    ]))
    merged.update(explicit_updates)
    if merged_preferences:
        merged["preferences"] = merged_preferences
    else:
        merged.pop("preferences", None)
    return merged


def derive_quest_from_answer(answer: str) -> QuestPayload | None:
    """Recover a validated Quest when the model described one but omitted the quest field."""
    title_match = re.search(
        r"QUEST\s*(?:—|-|:)?\s*(?:제목\s*:\s*)?([^\n(]{2,80})",
        answer,
        flags=re.IGNORECASE,
    )
    if not title_match:
        title_match = re.search(
            r"(?:^|\n)\s*미션\s*:\s*([^\n—]{2,80})",
            answer,
            flags=re.IGNORECASE,
        )
    if not title_match:
        return None
    title = title_match.group(1).strip(" :'\"-—")
    if not title:
        return None

    description_match = re.search(
        r"(?:수행\s*방법|수행법|미션)\s*:\s*(.+?)(?=\n\s*[-•]?\s*(?:성공\s*조건|난이도|위치)|$)",
        answer,
        flags=re.IGNORECASE | re.DOTALL,
    )
    description = (
        description_match.group(1).strip() if description_match else "채팅 안내에 따라 미션을 수행하고 서로 결과를 공유하세요."
    )

    difficulty_match = re.search(r"\b(EASY|NORMAL|HARD|SECRET)\b", answer, flags=re.IGNORECASE)
    difficulty = difficulty_match.group(1).upper() if difficulty_match else "NORMAL"
    xp_match = re.search(r"(50|100|150|200)\s*XP", answer, flags=re.IGNORECASE)
    xp_by_difficulty = {"EASY": 50, "NORMAL": 100, "HARD": 150, "SECRET": 200}
    xp = int(xp_match.group(1)) if xp_match else xp_by_difficulty[difficulty]
    location_match = re.search(r"(?:위치|장소)\s*:\s*([^\n]{1,80})", answer)
    location = location_match.group(1).strip() if location_match else None

    return QuestPayload(
        id=f"ai-{uuid.uuid4().hex[:12]}",
        title=title,
        description=description[:500],
        difficulty=difficulty,
        xp=xp,
        location=location,
        status="active",
    )


def search_context(question: str) -> tuple[str, list[str]]:
    """Return only chunks that satisfy the configured similarity threshold."""
    if vectorstore is None:
        return "", []
    results = vectorstore.similarity_search_with_relevance_scores(question, k=RAG_TOP_K)
    accepted = [(doc, score) for doc, score in results if score >= RAG_SCORE_THRESHOLD]
    context = "\n\n".join(doc.page_content.strip() for doc, _ in accepted)
    sources = sorted({Path(doc.metadata.get("source", "알 수 없음")).name for doc, _ in accepted})
    return context, sources


@asynccontextmanager
async def lifespan(_: FastAPI):
    """Initialize AI clients without preventing health checks on config errors."""
    global llm, embeddings, vectorstore, startup_warning
    if not API_KEY:
        startup_warning = "OPENAI_API_KEY 또는 API_KEY 환경변수가 필요합니다."
        yield
        return

    try:
        llm = ChatOpenAI(model=GPT_MODEL, api_key=API_KEY, base_url=BASE_URL)
        embeddings = OpenAIEmbeddings(
            model=EMBEDDING_MODEL, api_key=API_KEY, base_url=BASE_URL
        )
        vectorstore = Chroma(
            collection_name="gwangju_quest",
            embedding_function=embeddings,
            persist_directory=str(CHROMA_DB_PATH),
        )
        index_seed_documents()
    except Exception as exc:
        startup_warning = f"AI 초기화 실패: {exc}"
    yield


app = FastAPI(title="Gwangju Quest API", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origin_regex=r"https?://(localhost|127\.0\.0\.1)(:\d+)?",
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "ok" if llm is not None else "degraded",
        "ai_ready": llm is not None,
        "rag_ready": vectorstore is not None,
        "warning": startup_warning,
    }


@app.post("/chat")
def chat(req: ChatRequest) -> dict[str, Any]:
    if llm is None:
        raise HTTPException(status_code=503, detail=startup_warning or "AI가 준비되지 않았습니다.")

    context = ""
    sources: list[str] = []
    if req.use_rag:
        try:
            context, sources = search_context(build_search_query(req.message, req.history))
        except Exception as exc:
            print(f"RAG 검색 오류: {exc}")

    state_text = format_game_state(req.game_state)
    history_messages = format_history(req.history)
    travel_profile_text = format_mapping(req.profile, "아직 파악된 여행 조건이 없습니다.")
    completed_quests_text = format_completed_quests(req.completed_quests)
    if context:
        messages = RAG_PROMPT.format_messages(
            context=context,
            game_state=state_text,
            travel_profile=travel_profile_text,
            completed_quests=completed_quests_text,
            history=history_messages,
            question=req.message,
        )
        rag_used = True
        source_label = ", ".join(sources)
    else:
        messages = GENERAL_PROMPT.format_messages(
            game_state=state_text,
            travel_profile=travel_profile_text,
            completed_quests=completed_quests_text,
            history=history_messages,
            question=req.message,
        )
        rag_used = False
        source_label = "일반 AI 응답 (관련 문서 없음)" if req.use_rag else "일반 AI 응답"

    try:
        raw_answer = llm.invoke(messages).content
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"AI 응답 생성에 실패했습니다: {exc}") from exc

    raw_text = raw_answer if isinstance(raw_answer, str) else str(raw_answer)
    structured = parse_structured_response(raw_text)
    explicit_updates = extract_profile_updates(req.message)
    deterministic_intent = infer_intent(req.message)

    if structured is None:
        answer = raw_text
        profile_updates = explicit_updates
        recovered_quest = derive_quest_from_answer(answer)
        quest = recovered_quest.model_dump(exclude_none=True) if recovered_quest else None
        intent = deterministic_intent
    else:
        answer = structured.response
        profile_updates = merge_profile_updates(structured.profile_updates, explicit_updates)
        quest_payload = structured.quest or derive_quest_from_answer(answer)
        if quest_payload and not quest_payload.location and req.profile:
            current_area = req.profile.get("currentArea")
            if current_area:
                quest_payload.location = str(current_area)[:80]
        quest = quest_payload.model_dump(exclude_none=True) if quest_payload else None
        intent = deterministic_intent if deterministic_intent != "NONE" else structured.intent

    return {
        "response": answer,
        "answer": answer,
        "source": source_label,
        "rag_used": rag_used,
        "profile_updates": profile_updates,
        "quest": quest,
        "intent": intent,
    }


@app.post("/upload")
def upload_file(file: UploadFile = File(...)) -> dict[str, Any]:
    if vectorstore is None:
        raise HTTPException(status_code=503, detail=startup_warning or "RAG가 준비되지 않았습니다.")

    original_name = Path(file.filename or "").name
    extension = Path(original_name).suffix.lower()
    if extension not in {".txt", ".md", ".pdf"}:
        raise HTTPException(status_code=400, detail="txt, md, pdf 파일만 업로드할 수 있습니다.")

    save_path = DATA_DIR / original_name
    try:
        with save_path.open("wb") as output:
            shutil.copyfileobj(file.file, output)
        chunks_added = index_document(save_path)
        if chunks_added == 0:
            raise ValueError("읽을 수 있는 내용이 없습니다.")
        return {
            "success": True,
            "message": f"{original_name} 업로드 완료 ({chunks_added}개 청크)",
            "chunks_added": chunks_added,
        }
    except Exception as exc:
        if save_path.exists() and save_path.stat().st_size == 0:
            save_path.unlink()
        raise HTTPException(status_code=400, detail=f"파일 처리 실패: {exc}") from exc


@app.post("/reset-db")
def reset_database() -> dict[str, Any]:
    if vectorstore is None:
        raise HTTPException(status_code=503, detail="RAG가 준비되지 않았습니다.")
    ids = vectorstore.get().get("ids", [])
    if ids:
        vectorstore.delete(ids=ids)
    index_seed_documents()
    return {"success": True, "message": "벡터 DB를 초기화하고 기본 광주 데이터를 복원했습니다."}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="127.0.0.1", port=8000, reload=True)
