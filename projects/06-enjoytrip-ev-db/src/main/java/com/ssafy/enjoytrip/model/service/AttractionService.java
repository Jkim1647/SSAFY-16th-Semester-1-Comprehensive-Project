package com.ssafy.enjoytrip.model.service;

import java.util.List;

import com.ssafy.enjoytrip.model.dao.AttractionDao;
import com.ssafy.enjoytrip.model.dao.JdbcAttractionDao;
import com.ssafy.enjoytrip.model.dto.Attraction;
import com.ssafy.enjoytrip.model.dto.AttractionCondition;
import com.ssafy.enjoytrip.model.dto.CodeName;
import com.ssafy.enjoytrip.model.dto.Page;

/** F101~F103 관광지 조회. 비즈니스 규칙이 거의 없어 DAO 를 감싸는 얇은 계층이다. */
public class AttractionService {

    private static final AttractionService INSTANCE = new AttractionService();
    private final AttractionDao dao = JdbcAttractionDao.getInstance();

    private AttractionService() {
    }

    public static AttractionService getInstance() {
        return INSTANCE;
    }

    public Page<Attraction> search(AttractionCondition condition) {
        int total = dao.count(condition);
        int lastPage = Math.max(1, (int) Math.ceil((double) total / condition.getItemsPerPage()));
        if (condition.getCurrentPage() > lastPage) {
            condition = new AttractionCondition(condition.getSignguCode(), condition.getContentTypeId(),
                    condition.getKeyword(), lastPage, condition.getItemsPerPage());
        }
        return new Page<>(condition.toSearchCondition(), total, dao.search(condition));
    }

    public Attraction findById(int contentId) {
        return dao.findById(contentId);
    }

    public List<Attraction> findByIds(List<Integer> contentIds) {
        return dao.findByIds(contentIds);
    }

    public List<CodeName> signguList() {
        return dao.findSigngu();
    }

    public List<CodeName> contentTypes() {
        return dao.findContentTypes();
    }
}
