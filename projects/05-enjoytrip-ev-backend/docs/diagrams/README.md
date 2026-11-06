# README 다이어그램

아키텍처와 클래스 다이어그램의 Mermaid 원본은 같은 이름의 `.mmd`입니다. 유스케이스는 액터와 타원으로 기능을 나타내며, 회원과 관리자도 공개 조회 기능을 사용할 수 있습니다.

Mermaid CLI 12.0.0으로 생성했습니다. 예:

```sh
mmdc -i docs/diagrams/architecture.mmd -o docs/diagrams/architecture.svg -c docs/diagrams/mermaid-config.json -b white
```

`classes`도 같은 방법으로 재생성합니다. SVG 파일은 별도 라이브러리 없이 열 수 있고 브라우저에서 확대하거나 저장할 수 있습니다.

## README 클릭 확대

README에는 링크로 감싸지 않은 고해상도 PNG를 사용한다. SSAFY GitLab의 기본 Image viewer를 통해 같은 화면에서 확대하며 새 탭으로 이동시키지 않는다. SVG와 Mermaid 원본은 수정·재생성용으로 유지한다. PNG는 같은 원본에 `-s 2` 옵션을 주어 생성한다.

현재 유스케이스 SVG는 `python docs/diagrams/render_usecase.py`로 생성하며, README의 PNG는 이 SVG를 렌더링한 것이다. `usecase.mmd`는 별도의 Mermaid 관계도이고 `generate-usecase.py`는 앞선 그림의 생성 코드로 보관한다.
