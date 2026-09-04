"""Compare the current Gwangju RAG pipeline with a heading-aware improvement."""

from __future__ import annotations

import json
import os
import uuid
from pathlib import Path

import openai
import pandas as pd
from dotenv import load_dotenv
from langchain_chroma import Chroma
from langchain_core.documents import Document
from langchain_openai import ChatOpenAI, OpenAIEmbeddings
from langchain_text_splitters import MarkdownHeaderTextSplitter, RecursiveCharacterTextSplitter
from ragas import EvaluationDataset, evaluate
from ragas.metrics import AnswerRelevancy, ContextPrecision, Faithfulness, LLMContextRecall
from ragas.run_config import RunConfig

import main
from prompt import GENERAL_PROMPT, RAG_PROMPT


BASE_DIR = Path(__file__).resolve().parent
DATA_DIR = BASE_DIR / "data"
TEST_CASES_PATH = BASE_DIR / "gwangju_test_cases.json"
PUBLIC_DATA_FILE = "gwangju_public_official.md"

CONFIGS = {
    "baseline": {
        "description": "기존 고정 길이 청킹과 기존 12개 광주 문서",
        "chunking": "recursive-700-overlap-100",
        "top_k": 4,
        "threshold": 0.25,
        "include_public_data": False,
    },
    "improved": {
        "description": "Markdown 제목 보존 청킹과 공식 공공 관광 데이터",
        "chunking": "markdown-heading+recursive-450-overlap-60",
        "top_k": 5,
        "threshold": 0.20,
        "include_public_data": True,
    },
}


def patch_unsupported_temperature() -> None:
    """Ragas may pass temperature to models that reject that parameter."""

    original_create = openai.resources.chat.completions.Completions.create
    original_async_create = openai.resources.chat.completions.AsyncCompletions.create

    def safe_create(self, *args, **kwargs):
        kwargs.pop("temperature", None)
        return original_create(self, *args, **kwargs)

    async def safe_async_create(self, *args, **kwargs):
        kwargs.pop("temperature", None)
        return await original_async_create(self, *args, **kwargs)

    openai.resources.chat.completions.Completions.create = safe_create
    openai.resources.chat.completions.AsyncCompletions.create = safe_async_create


def load_documents(include_public_data: bool) -> list[Document]:
    documents: list[Document] = []
    for path in sorted(DATA_DIR.glob("gwangju_*.md")):
        if not include_public_data and path.name == PUBLIC_DATA_FILE:
            continue
        text = path.read_text(encoding="utf-8")
        documents.append(Document(page_content=text, metadata={"source": path.name}))
    return documents


def baseline_chunks(documents: list[Document]) -> list[Document]:
    splitter = RecursiveCharacterTextSplitter(
        chunk_size=700,
        chunk_overlap=100,
        separators=["\n\n", "\n", ". ", " ", ""],
    )
    return splitter.split_documents(documents)


def improved_chunks(documents: list[Document]) -> list[Document]:
    header_splitter = MarkdownHeaderTextSplitter(
        headers_to_split_on=[("#", "title"), ("##", "section"), ("###", "subsection")],
        strip_headers=False,
    )
    body_splitter = RecursiveCharacterTextSplitter(
        chunk_size=450,
        chunk_overlap=60,
        separators=["\n\n", "\n", ". ", " ", ""],
    )
    chunks: list[Document] = []
    for document in documents:
        sections = header_splitter.split_text(document.page_content)
        for section in sections:
            section.metadata["source"] = document.metadata["source"]
        chunks.extend(body_splitter.split_documents(sections))
    return chunks


def answer_question(llm: ChatOpenAI, question: str, contexts: list[str]) -> str:
    common = {
        "game_state": "레벨 1, XP 0, 현재 Quest 없음",
        "travel_profile": "아직 파악된 여행 조건이 없습니다.",
        "completed_quests": "완료한 Quest가 없습니다.",
        "history": [],
        "question": question,
    }
    if contexts:
        messages = RAG_PROMPT.format_messages(context="\n\n".join(contexts), **common)
    else:
        messages = GENERAL_PROMPT.format_messages(**common)
    raw = llm.invoke(messages).content
    raw_text = raw if isinstance(raw, str) else str(raw)
    structured = main.parse_structured_response(raw_text)
    return structured.response if structured else raw_text


def build_eval_rows(
    name: str,
    config: dict,
    llm: ChatOpenAI,
    embeddings: OpenAIEmbeddings,
    test_cases: list[dict],
) -> tuple[list[dict], int]:
    documents = load_documents(config["include_public_data"])
    chunks = improved_chunks(documents) if name == "improved" else baseline_chunks(documents)
    store = Chroma(
        collection_name=f"gwangju_eval_{name}_{uuid.uuid4().hex[:8]}",
        embedding_function=embeddings,
    )
    store.add_documents(chunks)
    rows: list[dict] = []
    try:
        for index, case in enumerate(test_cases, start=1):
            question = case["question"]
            print(f"[{name} {index}/{len(test_cases)}] {question}", flush=True)
            results = store.similarity_search_with_relevance_scores(
                question, k=config["top_k"]
            )
            accepted = [
                (document, score)
                for document, score in results
                if score >= config["threshold"]
            ]
            contexts = [document.page_content.strip() for document, _ in accepted]
            sources = sorted(
                {document.metadata.get("source", "unknown") for document, _ in accepted}
            )
            rows.append(
                {
                    "user_input": question,
                    "response": answer_question(llm, question, contexts),
                    "retrieved_contexts": contexts,
                    "reference": case["ground_truth"],
                    "retrieved_sources": ", ".join(sources),
                    "accepted_chunks": len(accepted),
                    "best_score": max((score for _, score in results), default=0.0),
                }
            )
    finally:
        store.delete_collection()
    return rows, len(chunks)


def evaluate_rows(
    name: str,
    rows: list[dict],
    llm: ChatOpenAI,
    embeddings: OpenAIEmbeddings,
) -> pd.DataFrame:
    ragas_rows = [
        {
            "user_input": row["user_input"],
            "response": row["response"],
            "retrieved_contexts": row["retrieved_contexts"],
            "reference": row["reference"],
        }
        for row in rows
    ]
    metrics = [
        Faithfulness(llm=llm),
        AnswerRelevancy(llm=llm),
        LLMContextRecall(llm=llm),
        ContextPrecision(llm=llm),
    ]
    result = evaluate(
        dataset=EvaluationDataset.from_list(ragas_rows),
        metrics=metrics,
        llm=llm,
        embeddings=embeddings,
        run_config=RunConfig(max_workers=2, max_retries=5, timeout=180),
    )
    frame = result.to_pandas()
    diagnostics = pd.DataFrame(
        [
            {
                "retrieved_sources": row["retrieved_sources"],
                "accepted_chunks": row["accepted_chunks"],
                "best_score": row["best_score"],
            }
            for row in rows
        ]
    )
    frame = pd.concat([frame.reset_index(drop=True), diagnostics], axis=1)
    frame.insert(0, "configuration", name)
    frame.to_csv(BASE_DIR / f"ragas_gwangju_{name}.csv", index=False, encoding="utf-8-sig")
    return frame


def metric_summary(frame: pd.DataFrame) -> dict[str, float]:
    return {
        metric: float(frame[metric].mean())
        for metric in [
            "faithfulness",
            "answer_relevancy",
            "context_recall",
            "context_precision",
        ]
    }


def write_comparison(
    frames: dict[str, pd.DataFrame], chunk_counts: dict[str, int]
) -> None:
    baseline = metric_summary(frames["baseline"])
    improved = metric_summary(frames["improved"])
    labels = {
        "faithfulness": "Faithfulness",
        "answer_relevancy": "Answer Relevancy",
        "context_recall": "Context Recall",
        "context_precision": "Context Precision",
    }
    lines = [
        "# Gwangju Quest RAGAS 성능 개선 비교",
        "",
        "## 평가 설계",
        "",
        "- 동일한 광주 질문 10개와 동일한 생성·임베딩 모델을 사용했다.",
        "- 기준선은 기존 700자/100자 중첩 고정 길이 청킹, top-k 4, 임계값 0.25다.",
        "- 개선형은 Markdown 제목 보존 후 450자/60자 중첩 청킹, top-k 5, 임계값 0.20을 사용한다.",
        "- 개선형에만 출처와 수집일이 명시된 공식 광주 관광 데이터를 포함했다.",
        "",
        "## 평균 결과",
        "",
        "| 지표 | 기준선 | 개선형 | 변화 |",
        "|---|---:|---:|---:|",
    ]
    for key, label in labels.items():
        delta = improved[key] - baseline[key]
        lines.append(
            f"| {label} | {baseline[key]:.4f} | {improved[key]:.4f} | {delta:+.4f} |"
        )
    lines.extend(
        [
            "",
            "## 구성 비교",
            "",
            f"- 기준선 청크 수: {chunk_counts['baseline']}",
            f"- 개선형 청크 수: {chunk_counts['improved']}",
            "- 세부 질문별 결과와 검색 출처는 각 CSV를 참고한다.",
            "",
            "## 해석 시 주의사항",
            "",
            "- 생성형 모델 기반 평가는 실행할 때마다 소폭 달라질 수 있다.",
            "- 데이터 추가와 청킹·검색 설정을 함께 변경했으므로 개선분 전체를 한 요인에만 귀속할 수 없다.",
            "- 운영시간과 휴무일은 변동 가능 정보이므로 답변에서 공식 출처 재확인을 안내해야 한다.",
            "",
        ]
    )
    (BASE_DIR / "ragas_gwangju_comparison.md").write_text(
        "\n".join(lines), encoding="utf-8"
    )


def main_run() -> None:
    load_dotenv(BASE_DIR / ".env")
    patch_unsupported_temperature()
    api_key = os.getenv("OPENAI_API_KEY") or os.getenv("API_KEY")
    if not api_key:
        raise RuntimeError("OPENAI_API_KEY 또는 API_KEY가 필요합니다.")
    base_url = os.getenv("OPENAI_BASE_URL") or os.getenv("base_url") or None
    model = os.getenv("GPT_MODEL", "gpt-5-mini")
    embedding_model = os.getenv("EMBEDDING_MODEL_NAME") or os.getenv(
        "EMBEDDING_MODEL", "text-embedding-3-small"
    )
    llm = ChatOpenAI(model=model, api_key=api_key, base_url=base_url)
    embeddings = OpenAIEmbeddings(
        model=embedding_model, api_key=api_key, base_url=base_url
    )
    main.llm = llm
    main.embeddings = embeddings
    test_cases = json.loads(TEST_CASES_PATH.read_text(encoding="utf-8"))
    frames: dict[str, pd.DataFrame] = {}
    chunk_counts: dict[str, int] = {}
    for name, config in CONFIGS.items():
        print(f"\n=== {name}: {config['description']} ===", flush=True)
        rows, chunk_count = build_eval_rows(name, config, llm, embeddings, test_cases)
        chunk_counts[name] = chunk_count
        frames[name] = evaluate_rows(name, rows, llm, embeddings)
        print(metric_summary(frames[name]), flush=True)
    write_comparison(frames, chunk_counts)
    print("\n평가 완료: ragas_gwangju_comparison.md", flush=True)


if __name__ == "__main__":
    main_run()

