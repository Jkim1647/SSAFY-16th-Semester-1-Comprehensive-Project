package com.ssafy.enjoytrip.model.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.ssafy.enjoytrip.model.dao.HotplaceDao;
import com.ssafy.enjoytrip.model.dao.JdbcHotplaceDao;
import com.ssafy.enjoytrip.model.dto.Hotplace;
import com.ssafy.enjoytrip.model.dto.HotplaceCondition;
import com.ssafy.enjoytrip.model.dto.Member;
import com.ssafy.enjoytrip.model.dto.Page;
import com.ssafy.enjoytrip.model.dto.PhotoUpload;
import com.ssafy.enjoytrip.util.PhotoStorage;

/** F105 회원 주도 핫플레이스 등록. 입력 규칙 검사, 작성자/관리자 권한, 사진 파일과 DB 행의 수명 관리를 맡는다. */
public class HotplaceService {

    private static final HotplaceService INSTANCE = new HotplaceService();

    public static final List<String> PLACE_TYPES = List.of("관광지", "음식점", "카페", "숙소", "충전소", "기타");
    public static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;
    /** 지도 마커로 한 번에 내려줄 최대 개수 (목록 페이징과 별개) */
    private static final int MAP_LIMIT = 500;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate MIN_VISIT = LocalDate.of(1950, 1, 1);
    // 대한민국 영역(제주 남쪽 ~ 휴전선 북쪽, 서해 ~ 독도)을 넉넉히 감싼 사각형
    private static final double MIN_LAT = 33.0, MAX_LAT = 38.7, MIN_LNG = 124.5, MAX_LNG = 132.0;

    private final HotplaceDao dao = JdbcHotplaceDao.getInstance();

    private HotplaceService() {
    }

    public static HotplaceService getInstance() {
        return INSTANCE;
    }

    public static LocalDate today() {
        return LocalDate.now(SEOUL);
    }

    public Page<Hotplace> search(HotplaceCondition condition) {
        return new Page<>(condition.toSearchCondition(), dao.count(condition), dao.search(condition));
    }

    /** 지도에 찍을 핫플레이스 (같은 필터, 최신순 최대 MAP_LIMIT 개). */
    public List<Hotplace> mapMarkers(String placeType, Long memberId) {
        return dao.search(new HotplaceCondition(placeType, memberId, 1, MAP_LIMIT));
    }

    public Hotplace findById(long hotplaceId) {
        return dao.findById(hotplaceId);
    }

    public boolean canEdit(Member user, Hotplace hotplace) {
        return user != null && hotplace != null && (user.isAdmin() || user.getMemberId() == hotplace.getMemberId());
    }

    /** 등록: 입력 검사 → 사진 저장 → DB 저장. DB 저장이 실패하면 방금 저장한 사진을 지워 고아 파일을 남기지 않는다. */
    public long regist(Member writer, Hotplace input, PhotoUpload photo) {
        Hotplace h = validated(input);
        String ext = checkPhoto(photo);
        h.setMemberId(writer.getMemberId());
        h.setPhotoFile(ext == null ? null : PhotoStorage.save(photo.data(), ext));
        try {
            return dao.insert(h);
        } catch (RuntimeException e) {
            PhotoStorage.delete(h.getPhotoFile());
            throw e;
        }
    }

    /**
     * 수정: 새 사진이 오면 교체, removePhoto 면 삭제, 둘 다 아니면 기존 사진 유지.
     * DB 갱신이 성공한 뒤에만 옛 파일을 지운다.
     */
    public void modify(Member user, long hotplaceId, Hotplace input, PhotoUpload photo, boolean removePhoto) {
        Hotplace old = requireEditable(user, hotplaceId);
        Hotplace h = validated(input);
        String ext = checkPhoto(photo);
        h.setHotplaceId(hotplaceId);
        if (ext != null) {
            h.setPhotoFile(PhotoStorage.save(photo.data(), ext));
        } else {
            h.setPhotoFile(removePhoto ? null : old.getPhotoFile());
        }
        try {
            dao.update(h);
        } catch (RuntimeException e) {
            if (ext != null) {
                PhotoStorage.delete(h.getPhotoFile());
            }
            throw e;
        }
        if (old.getPhotoFile() != null && !old.getPhotoFile().equals(h.getPhotoFile())) {
            PhotoStorage.delete(old.getPhotoFile());
        }
    }

    public void delete(Member user, long hotplaceId) {
        Hotplace old = requireEditable(user, hotplaceId);
        dao.delete(hotplaceId);
        PhotoStorage.delete(old.getPhotoFile());
    }

    private Hotplace requireEditable(Member user, long hotplaceId) {
        Hotplace old = dao.findById(hotplaceId);
        if (old == null) {
            throw new ValidationException("핫플레이스를 찾을 수 없습니다.");
        }
        if (!canEdit(user, old)) {
            throw new ValidationException("본인이 등록한 핫플레이스만 수정·삭제할 수 있습니다.");
        }
        return old;
    }

    /** 사용자 입력을 검사하고 저장할 값만 담은 새 객체를 돌려준다 (id·작성자·사진은 호출 측이 채운다). */
    private static Hotplace validated(Hotplace in) {
        String title = in.getTitle() == null ? "" : in.getTitle().trim();
        if (title.isEmpty() || title.length() > 100) {
            throw new ValidationException("장소 이름은 1~100자로 입력하세요.");
        }
        if (!PLACE_TYPES.contains(in.getPlaceType())) {
            throw new ValidationException("장소 유형을 목록에서 고르세요.");
        }
        LocalDate visited = in.getVisitedOn();
        if (visited == null) {
            throw new ValidationException("다녀온 날짜를 입력하세요.");
        }
        if (visited.isAfter(today())) {
            throw new ValidationException("다녀온 날짜는 오늘 이후로 고를 수 없습니다.");
        }
        if (visited.isBefore(MIN_VISIT)) {
            throw new ValidationException("다녀온 날짜를 다시 확인하세요.");
        }
        String desc = in.getDescription() == null ? "" : in.getDescription().trim();
        if (desc.length() > 2000) {
            throw new ValidationException("설명은 2000자 이하로 입력하세요.");
        }
        Double lat = in.getLatitude();
        Double lng = in.getLongitude();
        if (lat == null || lng == null || lat.isNaN() || lng.isNaN()) {
            throw new ValidationException("지도를 클릭해 위치를 선택하세요.");
        }
        if (lat < MIN_LAT || lat > MAX_LAT || lng < MIN_LNG || lng > MAX_LNG) {
            throw new ValidationException("위치는 국내(한반도·제주·독도 부근)에서만 선택할 수 있습니다.");
        }

        Hotplace h = new Hotplace();
        h.setTitle(title);
        h.setPlaceType(in.getPlaceType());
        h.setVisitedOn(visited);
        h.setDescription(desc.isEmpty() ? null : desc);
        h.setLatitude(lat);
        h.setLongitude(lng);
        return h;
    }

    /**
     * 사진 검사. 선언된 Content-Type 과 실제 파일 앞부분(매직 바이트)이 모두 허용 형식이고 서로 같아야 통과한다.
     * 사진이 없으면 null, 통과하면 저장할 확장자를 돌려준다.
     */
    private static String checkPhoto(PhotoUpload photo) {
        if (photo == null || photo.isEmpty()) {
            return null;
        }
        if (photo.data().length > MAX_PHOTO_BYTES) {
            throw new ValidationException("사진은 5MB 이하만 올릴 수 있습니다.");
        }
        String declared = PhotoStorage.extensionForContentType(photo.contentType());
        String detected = PhotoStorage.detectExtension(photo.data());
        if (declared == null || detected == null || !declared.equals(detected)) {
            throw new ValidationException("사진은 JPG, PNG, WEBP, GIF 이미지 파일만 올릴 수 있습니다.");
        }
        return detected;
    }
}
