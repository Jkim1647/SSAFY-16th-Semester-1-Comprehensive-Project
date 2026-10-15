package com.ssafy.enjoytrip.config;

import com.ssafy.enjoytrip.algorithm.security.PasswordHasher;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml 의 enjoytrip.* 설정 */
@ConfigurationProperties(prefix = "enjoytrip")
public class EnjoyTripProperties {

    /** CSV 데이터 폴더. 상대 경로면 실행 위치 기준으로 찾고, 없으면 상위 폴더에서 한 번 더 찾는다 (backend 모듈에서 실행할 때). */
    private String dataDir = "data";
    private final Password password = new Password();

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public Password getPassword() {
        return password;
    }

    public static class Password {
        /** 서버 비밀 값. 비어 있으면 시연용 값을 쓰고 경고를 남긴다. 운영에서는 환경 변수 ENJOYTRIP_PEPPER 로 넣는다. */
        private String pepper = "";
        private int iterations = PasswordHasher.DEFAULT_ITERATIONS;
        /** 동시에 계산할 수 있는 해시 수. 0 이면 CPU 코어 수의 절반 */
        private int maxConcurrent = 0;

        public String getPepper() {
            return pepper;
        }

        public void setPepper(String pepper) {
            this.pepper = pepper;
        }

        public int getIterations() {
            return iterations;
        }

        public void setIterations(int iterations) {
            this.iterations = iterations;
        }

        public int getMaxConcurrent() {
            return maxConcurrent;
        }

        public void setMaxConcurrent(int maxConcurrent) {
            this.maxConcurrent = maxConcurrent;
        }
    }
}
