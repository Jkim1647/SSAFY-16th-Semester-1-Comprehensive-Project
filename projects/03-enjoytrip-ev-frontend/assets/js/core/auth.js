import { users, session, posts, hotplaces, plans } from "./storage.js";

// 프론트 단독 과제라 실제 보안은 불가능하지만, 최소한 평문 저장은 피한다.
const hash = async (text) => {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("");
};

export const RULES = {
  id: { re: /^[a-z0-9]{4,16}$/, msg: "아이디는 영문 소문자/숫자 4~16자입니다." },
  password: { re: /^(?=.*[A-Za-z])(?=.*\d).{8,20}$/, msg: "비밀번호는 영문과 숫자를 포함한 8~20자입니다." },
  name: { re: /^.{2,20}$/, msg: "이름은 2~20자입니다." },
  email: { re: /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/, msg: "올바른 이메일 형식이 아닙니다." },
};

export const validate = (field, value) => (RULES[field].re.test(value) ? "" : RULES[field].msg);

/** 비밀번호 해시를 뺀 공개용 사용자 정보 */
const publicUser = ({ passwordHash, ...rest }) => rest;

export const currentUser = () => {
  const id = session.get();
  const user = id && users.get(id);
  return user ? publicUser(user) : null;
};

export const isIdTaken = (id) => Boolean(users.get(id));

export const signup = async ({ id, password, name, email }) => {
  if (isIdTaken(id)) throw new Error("이미 사용 중인 아이디입니다.");
  users.insert({
    id,
    passwordHash: await hash(password),
    name,
    email,
    // 'admin' 아이디로 가입하면 공지사항 작성 권한을 가진다 (데모용 규칙)
    role: id === "admin" ? "admin" : "user",
    createdAt: new Date().toISOString(),
  });
};

export const login = async (id, password) => {
  const user = users.get(id);
  if (!user || user.passwordHash !== (await hash(password))) {
    throw new Error("아이디 또는 비밀번호가 일치하지 않습니다.");
  }
  session.set(id);
  return publicUser(user);
};

export const logout = () => session.clear();

export const updateProfile = async (id, { name, email, password }) => {
  const patch = { name, email };
  if (password) patch.passwordHash = await hash(password);
  return publicUser(users.update(id, patch));
};

export const verifyPassword = async (id, password) => users.get(id)?.passwordHash === (await hash(password));

/** 비밀번호 찾기: 아이디 + 이메일이 일치하면 재설정 허용 */
export const canResetPassword = (id, email) => users.get(id)?.email === email;

export const resetPassword = async (id, password) => {
  users.update(id, { passwordHash: await hash(password) });
};

/** 회원 탈퇴: 계정과 본인이 만든 계획/핫플을 지우고, 게시글은 작성자 표시만 남긴다 */
export const withdraw = (id) => {
  plans.removeWhere((p) => p.userId === id);
  hotplaces.removeWhere((h) => h.userId === id);
  posts.all()
    .filter((p) => p.writerId === id)
    // writerId 도 비워야 같은 아이디로 재가입한 사람이 글 수정 권한을 얻지 않는다
    .forEach((p) => posts.update(p.id, { writerId: null, writerName: "(탈퇴한 회원)" }));
  users.remove(id);
  session.clear();
};

/** 로그인이 필요한 페이지에서 호출. 비로그인이면 로그인 페이지로 보낸 뒤 되돌아온다. */
export const requireLogin = () => {
  const user = currentUser();
  if (!user) {
    const back = encodeURIComponent(location.pathname.split("/").pop() + location.search);
    location.replace(`login.html?redirect=${back}`);
  }
  return user;
};
