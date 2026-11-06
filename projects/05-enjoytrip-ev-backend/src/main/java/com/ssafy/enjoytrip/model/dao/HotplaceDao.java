package com.ssafy.enjoytrip.model.dao;

import java.util.List;

import com.ssafy.enjoytrip.model.dto.Hotplace;
import com.ssafy.enjoytrip.model.dto.HotplaceCondition;

public interface HotplaceDao {

    long insert(Hotplace hotplace);

    /** 제목·유형·날짜·설명·위치·사진 파일명을 바꾼다. 바뀐 행 수를 돌려준다. */
    int update(Hotplace hotplace);

    int delete(long hotplaceId);

    /** 작성자 이름(writerName)까지 채워서 돌려준다. 없으면 null. */
    Hotplace findById(long hotplaceId);

    int count(HotplaceCondition condition);

    /** 최신 등록순으로 condition 의 페이지 범위만 가져온다. */
    List<Hotplace> search(HotplaceCondition condition);

    /** 회원 탈퇴 시 DB 행은 ON DELETE CASCADE 로 지워지므로, 그 전에 지울 사진 파일명을 모아 둔다. */
    List<String> findPhotoFilesByMember(long memberId);
}
