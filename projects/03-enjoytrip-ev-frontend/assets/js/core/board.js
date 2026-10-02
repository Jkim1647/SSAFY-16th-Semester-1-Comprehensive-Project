// F109 공지사항 · F110 자유게시판 공통 로직
import { posts } from "./storage.js";
import { uid } from "./util.js";

export const BOARDS = {
  notice: { label: "공지사항", desc: "EnjoyTrip EV 운영 소식을 알려 드립니다." },
  free: { label: "자유게시판", desc: "여행 후기와 충전 팁을 자유롭게 나눠요." },
};

export const boardType = (value) => (value in BOARDS ? value : "free");

/** 공지사항은 관리자만, 자유게시판은 로그인 회원 누구나 */
export const canWrite = (user, type) => Boolean(user) && (type === "free" || user.role === "admin");
/** 수정·삭제는 작성자 본인 (공지는 관리자도 가능) */
export const canEdit = (user, post) =>
  Boolean(user) && (post.writerId === user.id || (post.type === "notice" && user.role === "admin"));

export const LIMITS = { title: 100, content: 2000 };

// 처음 방문했을 때 빈 화면 대신 보여 줄 공지 1건
const seed = () => {
  if (posts.all().length > 0 || localStorage.getItem("enjoytrip.seeded")) return;
  posts.insert({
    id: uid(),
    type: "notice",
    title: "EnjoyTrip EV 오픈 안내",
    content:
      "전기차로 떠나는 드라이브 여행 플래너 EnjoyTrip EV 를 오픈했습니다.\n\n" +
      "· 관광지 조회: 지역·유형별 관광지를 지도에서 확인\n" +
      "· 여행 계획: 장소 순서를 끌어서 바꾸고, 경로 주변 충전소 확인\n" +
      "· 핫플레이스: 다녀온 숨은 명소를 사진과 함께 공유\n\n" +
      "모든 데이터는 이 브라우저(localStorage)에만 저장됩니다.",
    writerId: null,
    writerName: "운영자",
    views: 0,
    createdAt: new Date().toISOString(),
    updatedAt: null,
  });
  localStorage.setItem("enjoytrip.seeded", "1");
};
seed();

export const listPosts = ({ type, query = "", page = 1, size = 10 }) => {
  const q = query.trim().toLowerCase();
  const rows = posts
    .filter((p) => p.type === type)
    .filter((p) => !q || p.title.toLowerCase().includes(q) || p.content.toLowerCase().includes(q))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const totalPages = Math.max(1, Math.ceil(rows.length / size));
  const current = Math.min(Math.max(1, page), totalPages);
  return {
    total: rows.length,
    totalPages,
    page: current,
    // 목록 번호: 전체 개수에서 거꾸로 센다
    items: rows.slice((current - 1) * size, current * size).map((p, i) => ({ ...p, no: rows.length - (current - 1) * size - i })),
  };
};

export const getPost = (id) => posts.get(id);

/** 같은 탭에서 새로고침할 때마다 조회수가 오르지 않도록 세션당 1회만 센다 */
export const increaseViews = (id) => {
  const key = `enjoytrip.viewed.${id}`;
  if (sessionStorage.getItem(key)) return posts.get(id);
  sessionStorage.setItem(key, "1");
  const post = posts.get(id);
  return post ? posts.update(id, { views: (post.views ?? 0) + 1 }) : null;
};

export const createPost = ({ type, title, content, user }) =>
  posts.insert({
    id: uid(),
    type,
    title,
    content,
    writerId: user.id,
    writerName: user.name,
    views: 0,
    createdAt: new Date().toISOString(),
    updatedAt: null,
  });

export const updatePost = (id, { title, content }) => posts.update(id, { title, content, updatedAt: new Date().toISOString() });

export const deletePost = (id) => posts.remove(id);
