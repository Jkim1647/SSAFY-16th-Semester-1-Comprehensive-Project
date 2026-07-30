package com.ssafy.trip.view;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

import com.ssafy.trip.model.dto.TripDto;
import com.ssafy.trip.model.dto.TripSearchDto;
import com.ssafy.trip.model.service.TripService;
import com.ssafy.trip.model.service.TripServiceImpl;

public class TripInfoView {

	/** model들 */
	private TripService tripService;

	/** main 화면 */
	private JFrame frame;

	/** 관광지 이미지 표시 Panel */
	private TripImagePanel imgPanel;
	private JLabel[] tripInfoL;
	private JCheckBox visitCheckBox;
	private StampMapPanel stampMapPanel;
	private JButton festivalBt;
	private JButton storeBt;
	private JButton sunBt;
	private JButton weatherBt;
	private JButton photoBt;
	private JButton chargerBt;

	/** 조회 조건 */
	private JComboBox<String> findC;
	private JTextField wordTf;
	private JButton searchBt;

	/** 조회 내용 표시할 table */
	private DefaultTableModel tripModel;
	private JTable tripTable;
	private JScrollPane tripPan;
	private String[] title = { "번호", "관광지명", "도로명주소", "지번주소", "전화번호" };

	/** 검색 조건 */
	private String key;
	private String[] choice = { "검색조건선택", "관광지명", "주소" };
	/** 검색할 단어 */
	private String word;

	/** 화면에 표시하고 있는 주택 */
	private TripDto curTrip;
	private List<TripDto> allTrips;
	private Set<Integer> visitedTrips;
	private boolean updatingVisitCheckBox;
	private File visitedFile;
	private AdvancedInfoStore advancedInfoStore;

	public TripInfoView() {
		/* Service들 생성 */
		tripService = new TripServiceImpl();
		allTrips = tripService.searchAll(new TripSearchDto());
		visitedFile = new File("res", "visited-trips.txt");
		visitedTrips = loadVisitedTrips();
		advancedInfoStore = new AdvancedInfoStore(new File("res"));

		/* 메인 화면 설정 */
		frame = new JFrame("Enjoy! Trip - 즐거운 여행");
//		frame.addWindowListener(new WindowAdapter() {
//			public void windowClosing(WindowEvent e){
//				frame.dispose();
//			}
//		});

		setMain();

		frame.setSize(1200, 800);
		frame.setResizable(true);
		frame.setVisible(true);
		showTripInfo(0);
	}

	private void showTripInfo(int num) {
		curTrip = tripService.search(num);
		if (curTrip == null) {
			return;
		}

		tripInfoL[0].setText("");
		tripInfoL[1].setText(getVisitedStatusText());
		tripInfoL[2].setText(curTrip.getTouristDestination());
		tripInfoL[3].setText(curTrip.getStreetAddress());
		tripInfoL[4].setText(curTrip.getLotAddress());
		tripInfoL[5].setText(curTrip.getLat() + "");
		tripInfoL[6].setText(curTrip.getLng() + "");
		tripInfoL[7].setText(curTrip.getTel());
		tripInfoL[8].setText(curTrip.getInfo());
		tripInfoL[9].setText("");

		ImageIcon icon = null;
		if (curTrip.getImg() != null && curTrip.getImg().trim().length() != 0) {
			String img = curTrip.getImg();
			File file = new File("img", img);

			if (!file.exists())
				img = "no_image.jpg";
			icon = new ImageIcon("img/" + img);

		} else {
			icon = new ImageIcon("img/no_image.jpg");
		}
		imgPanel.setImage(icon.getImage());

		updatingVisitCheckBox = true;
		visitCheckBox.setSelected(visitedTrips.contains(curTrip.getNum()));
		updatingVisitCheckBox = false;
		stampMapPanel.setSelectedTrip(curTrip);
	}

	/** 메인 화면인 관광지 목록을 위한 화면 셋팅하는 메서드 */
	public void setMain() {

		/* 왼쪽 화면을 위한 설정 */
		JPanel left = new JPanel(new BorderLayout());
		JPanel leftCenter = new JPanel(new BorderLayout(0, 10));
		JPanel leftR = new JPanel(new GridBagLayout());
		leftR.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

		String[] info = { "방문체크", "방문현황", "관광지명", "도로명주소", "지번주소", "위도", "경도", "전화번호", "관광지정보", "" };
		int size = info.length;
		JLabel infoL[] = new JLabel[size];
		tripInfoL = new JLabel[size];
		for (int i = 0; i < size; i++) {
			infoL[i] = new JLabel(info[i]);
			tripInfoL[i] = new JLabel("");
			GridBagConstraints labelGbc = new GridBagConstraints();
			labelGbc.gridx = 0;
			labelGbc.gridy = i;
			labelGbc.anchor = GridBagConstraints.NORTHWEST;
			labelGbc.insets = new java.awt.Insets(4, 0, 4, 10);
			labelGbc.weightx = 0.0;
			leftR.add(infoL[i], labelGbc);

			GridBagConstraints valueGbc = new GridBagConstraints();
			valueGbc.gridx = 1;
			valueGbc.gridy = i;
			valueGbc.anchor = GridBagConstraints.NORTHWEST;
			valueGbc.fill = GridBagConstraints.HORIZONTAL;
			valueGbc.insets = new java.awt.Insets(4, 0, 4, 0);
			valueGbc.weightx = 1.0;
			if (i == 0) {
				visitCheckBox = new JCheckBox("다녀온 곳");
				visitCheckBox.setFocusPainted(false);
				leftR.add(visitCheckBox, valueGbc);
			} else if (i == 9) {
				JPanel advancedPanel = new JPanel(new GridLayout(2, 3, 5, 5));
				festivalBt = new JButton("지역 축제");
				storeBt = new JButton("상권 조회");
				sunBt = new JButton("일출/일몰");
				weatherBt = new JButton("기간 날씨");
				photoBt = new JButton("추천 사진");
				chargerBt = new JButton("충전소");
				advancedPanel.add(festivalBt);
				advancedPanel.add(storeBt);
				advancedPanel.add(sunBt);
				advancedPanel.add(weatherBt);
				advancedPanel.add(photoBt);
				advancedPanel.add(chargerBt);
				valueGbc.insets = new java.awt.Insets(6, 0, 0, 0);
				leftR.add(advancedPanel, valueGbc);
			} else {
				leftR.add(tripInfoL[i], valueGbc);
			}
		}
		imgPanel = new TripImagePanel();
		imgPanel.setPreferredSize(new Dimension(0, 280));
		leftCenter.add(imgPanel, "Center");
		JPanel detailWrap = new JPanel(new BorderLayout());
		detailWrap.add(leftR, "North");
		JScrollPane detailScrollPane = new JScrollPane(detailWrap);
		detailScrollPane.setPreferredSize(new Dimension(0, 330));
		leftCenter.add(detailScrollPane, "South");

		left.add(new JLabel("관광지 정보", JLabel.CENTER), "North");
		left.add(leftCenter, "Center");

		/* 오른쪽 화면을 위한 설정 */
		JPanel right = new JPanel(new BorderLayout());
		JPanel rightTop = new JPanel(new BorderLayout());
		rightTop.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

		JPanel rightTop2 = new JPanel(new BorderLayout(5, 0));
		String[] item = { "검색조건선택", "관광지명", "주소" };
		findC = new JComboBox<String>(item);
		findC.setPreferredSize(new Dimension(150, 28));
		wordTf = new JTextField();
		searchBt = new JButton("검색");
		searchBt.setPreferredSize(new Dimension(120, 28));

		rightTop2.add(findC, BorderLayout.WEST);
		rightTop2.add(wordTf, BorderLayout.CENTER);
		rightTop2.add(searchBt, BorderLayout.EAST);

		rightTop.add(rightTop2, BorderLayout.CENTER);

		JPanel rightCenter = new JPanel(new BorderLayout());
		tripModel = new DefaultTableModel(title, 20) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		tripTable = new JTable(tripModel);
		tripPan = new JScrollPane(tripTable);
		tripTable.setColumnSelectionAllowed(false);
		tripTable.setRowSelectionAllowed(true);
		tripTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		rightCenter.add(new JLabel("관광지 정보", JLabel.CENTER), "North");
		rightCenter.add(tripPan, "Center");
		stampMapPanel = new StampMapPanel(allTrips, visitedTrips);
		stampMapPanel.setStampSelectionListener(trip -> showTripFromStamp(trip));
		stampMapPanel.setPreferredSize(new Dimension(0, 340));
		rightCenter.add(stampMapPanel, "South");

		right.add(rightTop, "North");
		right.add(rightCenter, "Center");

		JPanel mainP = new JPanel(new GridLayout(1, 2));

		mainP.add(left);
		mainP.add(right);

		mainP.setBorder(BorderFactory.createEmptyBorder(20, 10, 10, 10));
		frame.add(mainP, "Center");

		tripTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent e) {
				int row = tripTable.getSelectedRow();
				if (row < 0) {
					return;
				}
				int modelRow = tripTable.convertRowIndexToModel(row);
				int code = Integer.parseInt(((String) tripModel.getValueAt(modelRow, 0)).trim());
				showTripInfo(code);
			}
		});

		searchBt.addActionListener(e -> searchTrips());
		visitCheckBox.addActionListener(e -> changeVisitedTrip());
		festivalBt.addActionListener(e -> showFestivalInfo());
		storeBt.addActionListener(e -> showStoreInfo());
		sunBt.addActionListener(e -> showSunInfo());
		weatherBt.addActionListener(e -> showWeatherInfo());
		photoBt.addActionListener(e -> showPhotoInfo());
		chargerBt.addActionListener(e -> showChargerInfo());

		showTrips();
	}

	private void showWeatherInfo() {
		if (curTrip == null) {
			return;
		}

		JTextField startField = new JTextField(LocalDate.now().toString());
		JTextField endField = new JTextField(LocalDate.now().plusDays(4).toString());
		JPanel inputPanel = new JPanel(new GridLayout(2, 2, 5, 5));
		inputPanel.add(new JLabel("시작일(yyyy-MM-dd)"));
		inputPanel.add(startField);
		inputPanel.add(new JLabel("종료일(yyyy-MM-dd)"));
		inputPanel.add(endField);

		int result = JOptionPane.showConfirmDialog(frame, inputPanel, "기간별 날씨 조회", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);
		if (result != JOptionPane.OK_OPTION) {
			return;
		}

		try {
			LocalDate startDate = LocalDate.parse(startField.getText().trim());
			LocalDate endDate = LocalDate.parse(endField.getText().trim());
			if (endDate.isBefore(startDate)) {
				JOptionPane.showMessageDialog(frame, "종료일은 시작일 이후로 입력하세요.", "기간별 날씨",
						JOptionPane.WARNING_MESSAGE);
				return;
			}
			if (startDate.plusDays(14).isBefore(endDate)) {
				JOptionPane.showMessageDialog(frame, "조회 기간은 최대 15일입니다.", "기간별 날씨",
						JOptionPane.WARNING_MESSAGE);
				return;
			}

			String[] columns = { "날짜", "지역", "날씨", "최저", "최고", "강수", "메모" };
			showAdvancedInfo("기간별 날씨", columns, advancedInfoStore.buildWeatherRows(curTrip, startDate, endDate));
		} catch (Exception e) {
			JOptionPane.showMessageDialog(frame, "날짜는 yyyy-MM-dd 형식으로 입력하세요.", "기간별 날씨",
					JOptionPane.WARNING_MESSAGE);
		}
	}

	private void showPhotoInfo() {
		if (curTrip == null) {
			return;
		}

		List<String[]> photos = advancedInfoStore.findPhotos(curTrip);
		if (photos.isEmpty()) {
			photos = buildFallbackPhotos();
		}

		JDialog dialog = new JDialog(frame, "관광지 추천 사진", false);
		JPanel gallery = new JPanel(new GridLayout(0, 2, 10, 10));
		gallery.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		for (String[] photo : photos) {
			String fileName = photo.length > 1 ? photo[1] : "no_image.jpg";
			String title = photo.length > 2 ? photo[2] : curTrip.getTouristDestination();
			String description = photo.length > 3 ? photo[3] : "";
			gallery.add(createPhotoCard(fileName, title, description));
		}

		dialog.setLayout(new BorderLayout());
		dialog.add(new JLabel(curTrip.getTouristDestination() + " 추천 사진", JLabel.CENTER), "North");
		dialog.add(new JScrollPane(gallery), "Center");
		dialog.setSize(760, 560);
		dialog.setLocationRelativeTo(frame);
		dialog.setVisible(true);
	}

	private JPanel createPhotoCard(String fileName, String title, String description) {
		JPanel card = new JPanel(new BorderLayout(0, 5));
		card.setBorder(BorderFactory.createLineBorder(new Color(210, 210, 210)));

		File file = new File("img", fileName);
		if (!file.exists()) {
			fileName = "no_image.jpg";
		}
		ImageIcon icon = new ImageIcon("img/" + fileName);
		Image image = icon.getImage().getScaledInstance(320, 180, Image.SCALE_SMOOTH);
		JLabel imageLabel = new JLabel(new ImageIcon(image));
		imageLabel.setHorizontalAlignment(JLabel.CENTER);

		JLabel titleLabel = new JLabel(title, JLabel.CENTER);
		titleLabel.setFont(new Font("Dialog", Font.BOLD, 13));
		JLabel descLabel = new JLabel(description, JLabel.CENTER);

		JPanel textPanel = new JPanel(new GridLayout(2, 1));
		textPanel.add(titleLabel);
		textPanel.add(descLabel);

		card.add(imageLabel, "Center");
		card.add(textPanel, "South");
		return card;
	}

	private List<String[]> buildFallbackPhotos() {
		List<String[]> photos = new ArrayList<String[]>();
		String currentImage = curTrip.getImg() == null || curTrip.getImg().trim().length() == 0 ? "no_image.jpg"
				: curTrip.getImg();
		photos.add(new String[] { "기본", currentImage, curTrip.getTouristDestination(), "선택한 관광지 대표 이미지" });
		photos.add(new String[] { "추천", "image03.jpg", "여행지 풍경", "함께 보기 좋은 관광지 이미지" });
		photos.add(new String[] { "추천", "image07.jpg", "자연 경관", "여행 코스 참고 이미지" });
		photos.add(new String[] { "추천", "image10.jpg", "지역 명소", "주변 관광지 추천 이미지" });
		return photos;
	}

	private void showChargerInfo() {
		if (curTrip == null) {
			return;
		}
		String[] columns = { "지역", "충전소명", "주소", "상태", "충전기", "위도", "경도" };
		showAdvancedInfo("전기자동차 충전소", columns, advancedInfoStore.findChargers(curTrip));
	}

	private void showSunInfo() {
		if (curTrip == null) {
			return;
		}

		double[] coordinate = getTripCoordinate(curTrip);
		if (coordinate == null) {
			JOptionPane.showMessageDialog(frame, "일출/일몰 계산에 사용할 위치 정보를 찾지 못했습니다.", "일출/일몰 시각",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		LocalDate today = LocalDate.now();
		SunTimes sunTimes = calculateSunTimes(today, coordinate[0], coordinate[1], 9);
		DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

		String message = curTrip.getTouristDestination() + "\n"
				+ "날짜: " + today.format(dateFormatter) + "\n"
				+ "기준 위치: 위도 " + String.format("%.4f", coordinate[0]) + ", 경도 "
				+ String.format("%.4f", coordinate[1]) + "\n"
				+ "일출: " + sunTimes.sunrise.format(timeFormatter) + "\n"
				+ "일몰: " + sunTimes.sunset.format(timeFormatter) + "\n"
				+ "표시 시각: 한국 표준시(KST)";

		JOptionPane.showMessageDialog(frame, message, "일출/일몰 시각", JOptionPane.INFORMATION_MESSAGE);
	}

	private void showTripFromStamp(TripDto trip) {
		showTripInfo(trip.getNum());
		selectTripRow(trip.getNum());
	}

	private void selectTripRow(int tripNum) {
		for (int row = 0; row < tripModel.getRowCount(); row++) {
			Object value = tripModel.getValueAt(row, 0);
			if (value == null) {
				continue;
			}
			if (Integer.parseInt(value.toString().trim()) == tripNum) {
				int viewRow = tripTable.convertRowIndexToView(row);
				tripTable.setRowSelectionInterval(viewRow, viewRow);
				Rectangle rect = tripTable.getCellRect(viewRow, 0, true);
				tripTable.scrollRectToVisible(rect);
				return;
			}
		}
		tripTable.clearSelection();
	}

	private void showFestivalInfo() {
		if (curTrip == null) {
			return;
		}
		String[] columns = { "지역", "축제명", "기간", "장소", "소개" };
		showAdvancedInfo("지역 축제 정보", columns, advancedInfoStore.findFestivals(curTrip));
	}

	private void showStoreInfo() {
		if (curTrip == null) {
			return;
		}
		String[] columns = { "지역", "상권명", "업종", "주소", "비고" };
		showAdvancedInfo("주변 상권 정보", columns, advancedInfoStore.findStores(curTrip));
	}

	private void showAdvancedInfo(String title, String[] columns, List<String[]> rows) {
		if (rows.isEmpty()) {
			JOptionPane.showMessageDialog(frame, "선택한 관광지 주변 정보가 없습니다.", title, JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		String[][] data = new String[rows.size()][columns.length];
		for (int i = 0; i < rows.size(); i++) {
			String[] row = rows.get(i);
			for (int j = 0; j < columns.length; j++) {
				data[i][j] = j < row.length ? row[j] : "";
			}
		}

		DefaultTableModel model = new DefaultTableModel(data, columns) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};

		JTable table = new JTable(model);
		table.setRowHeight(24);

		JDialog dialog = new JDialog(frame, title, false);
		dialog.setLayout(new BorderLayout(0, 10));
		String dialogTitle = title.equals("기간별 날씨") ? curTrip.getTouristDestination() + " " + title
				: curTrip.getTouristDestination() + " 주변 " + title;
		dialog.add(new JLabel(dialogTitle, JLabel.CENTER), "North");
		dialog.add(new JScrollPane(table), "Center");
		dialog.setSize(760, 320);
		dialog.setLocationRelativeTo(frame);
		dialog.setVisible(true);
	}

	private SunTimes calculateSunTimes(LocalDate date, double latitude, double longitude, double utcOffset) {
		LocalTime sunrise = calculateSunTime(date, latitude, longitude, utcOffset, true);
		LocalTime sunset = calculateSunTime(date, latitude, longitude, utcOffset, false);
		return new SunTimes(sunrise, sunset);
	}

	private LocalTime calculateSunTime(LocalDate date, double latitude, double longitude, double utcOffset,
			boolean sunrise) {
		int dayOfYear = date.getDayOfYear();
		double lngHour = longitude / 15.0;
		double approxTime = dayOfYear + ((sunrise ? 6 : 18) - lngHour) / 24.0;

		double meanAnomaly = (0.9856 * approxTime) - 3.289;
		double trueLongitude = meanAnomaly
				+ (1.916 * Math.sin(Math.toRadians(meanAnomaly)))
				+ (0.020 * Math.sin(Math.toRadians(2 * meanAnomaly)))
				+ 282.634;
		trueLongitude = normalizeDegrees(trueLongitude);

		double rightAscension = Math.toDegrees(Math.atan(0.91764 * Math.tan(Math.toRadians(trueLongitude))));
		rightAscension = normalizeDegrees(rightAscension);
		double longitudeQuadrant = Math.floor(trueLongitude / 90.0) * 90.0;
		double ascensionQuadrant = Math.floor(rightAscension / 90.0) * 90.0;
		rightAscension = (rightAscension + longitudeQuadrant - ascensionQuadrant) / 15.0;

		double sinDeclination = 0.39782 * Math.sin(Math.toRadians(trueLongitude));
		double cosDeclination = Math.cos(Math.asin(sinDeclination));
		double zenith = 90.833;
		double cosHourAngle = (Math.cos(Math.toRadians(zenith))
				- (sinDeclination * Math.sin(Math.toRadians(latitude))))
				/ (cosDeclination * Math.cos(Math.toRadians(latitude)));

		cosHourAngle = Math.max(-1.0, Math.min(1.0, cosHourAngle));
		double hourAngle = Math.toDegrees(Math.acos(cosHourAngle));
		if (sunrise) {
			hourAngle = 360.0 - hourAngle;
		}
		hourAngle /= 15.0;

		double localMeanTime = hourAngle + rightAscension - (0.06571 * approxTime) - 6.622;
		double utcTime = normalizeHours(localMeanTime - lngHour);
		double localTime = normalizeHours(utcTime + utcOffset);
		int totalMinutes = (int) Math.round(localTime * 60.0);
		totalMinutes = ((totalMinutes % 1440) + 1440) % 1440;
		return LocalTime.of(totalMinutes / 60, totalMinutes % 60);
	}

	private double normalizeDegrees(double value) {
		value %= 360.0;
		return value < 0 ? value + 360.0 : value;
	}

	private double normalizeHours(double value) {
		value %= 24.0;
		return value < 0 ? value + 24.0 : value;
	}

	private double[] getTripCoordinate(TripDto trip) {
		if (trip.getLat() >= 32.0 && trip.getLat() <= 39.5 && trip.getLng() >= 124.0 && trip.getLng() <= 132.5) {
			return new double[] { trip.getLat(), trip.getLng() };
		}

		String address = nvl(trip.getStreetAddress()) + " " + nvl(trip.getLotAddress());
		if (address.contains("서울")) return new double[] { 37.5665, 126.9780 };
		if (address.contains("부산")) return new double[] { 35.1796, 129.0756 };
		if (address.contains("대구")) return new double[] { 35.8714, 128.6014 };
		if (address.contains("인천")) return new double[] { 37.4563, 126.7052 };
		if (address.contains("광주")) return new double[] { 35.1595, 126.8526 };
		if (address.contains("대전")) return new double[] { 36.3504, 127.3845 };
		if (address.contains("울산")) return new double[] { 35.5384, 129.3114 };
		if (address.contains("세종")) return new double[] { 36.4801, 127.2890 };
		if (address.contains("경기")) return new double[] { 37.4138, 127.5183 };
		if (address.contains("강원")) return new double[] { 37.8228, 128.1555 };
		if (address.contains("충청북도") || address.contains("충북")) return new double[] { 36.8000, 127.7000 };
		if (address.contains("충청남도") || address.contains("충남")) return new double[] { 36.5184, 126.8000 };
		if (address.contains("전북특별자치도") || address.contains("전라북도") || address.contains("전북")) return new double[] { 35.7175, 127.1530 };
		if (address.contains("전라남도") || address.contains("전남")) return new double[] { 34.8679, 126.9910 };
		if (address.contains("경상북도") || address.contains("경북")) return new double[] { 36.4919, 128.8889 };
		if (address.contains("경상남도") || address.contains("경남")) return new double[] { 35.4606, 128.2132 };
		if (address.contains("제주")) return new double[] { 33.4996, 126.5312 };
		return null;
	}

	private String nvl(String text) {
		return text == null ? "" : text;
	}

	private void changeVisitedTrip() {
		if (updatingVisitCheckBox || curTrip == null) {
			return;
		}

		if (visitCheckBox.isSelected()) {
			visitedTrips.add(curTrip.getNum());
		} else {
			visitedTrips.remove(curTrip.getNum());
		}

		saveVisitedTrips();
		tripInfoL[1].setText(getVisitedStatusText());
		stampMapPanel.repaint();
	}

	private String getVisitedStatusText() {
		return "스탬프 " + visitedTrips.size() + "개";
	}

	private Set<Integer> loadVisitedTrips() {
		Set<Integer> visits = new HashSet<Integer>();
		if (!visitedFile.exists()) {
			return visits;
		}

		try (BufferedReader br = new BufferedReader(new FileReader(visitedFile))) {
			String line;
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.length() == 0) {
					continue;
				}
				visits.add(Integer.parseInt(line));
			}
		} catch (IOException | NumberFormatException e) {
			System.out.println("방문 기록을 읽지 못했습니다: " + e.getMessage());
		}
		return visits;
	}

	private void saveVisitedTrips() {
		File parent = visitedFile.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}

		try (PrintWriter out = new PrintWriter(new FileWriter(visitedFile))) {
			for (Integer num : visitedTrips) {
				out.println(num);
			}
		} catch (IOException e) {
			System.out.println("방문 기록을 저장하지 못했습니다: " + e.getMessage());
		}
	}

	/** 검색 조건에 맞는 관광지 검색 */
	private void searchTrips() {
		word = wordTf.getText().trim();
		key = choice[findC.getSelectedIndex()];
		showTrips();
	}

	/**
	 * 관광지 목록을 갱신하기 위한 메서드
	 */
	public void showTrips() {
		TripSearchDto tripSearchDto = new TripSearchDto();
		if (key != null) {
			if (key.equals("관광지명")) {
				tripSearchDto.setTouristDestination(word);
			} else if (key.equals("주소")) {
				tripSearchDto.setSido(word);
			}
		}

		if (word == null || word.trim().length() == 0)
			findC.setSelectedIndex(0);

		List<TripDto> trips = tripService.searchAll(tripSearchDto);
		if (trips != null) {
			int i = 0;
			String[][] data = new String[trips.size()][5];
			for (TripDto trip : trips) {
				data[i][0] = "" + trip.getNum();
				data[i][1] = trip.getTouristDestination();
				data[i][2] = trip.getStreetAddress();
				data[i][3] = trip.getLotAddress();
				data[i++][4] = trip.getTel();
			}
			tripModel.setDataVector(data, title);
		}
	}

//	public static void main(String[] args) {
//		new TripInfoView();
//	}

	private static class SunTimes {
		private LocalTime sunrise;
		private LocalTime sunset;

		public SunTimes(LocalTime sunrise, LocalTime sunset) {
			this.sunrise = sunrise;
			this.sunset = sunset;
		}
	}

	private static class TripImagePanel extends JPanel {
		private Image image;

		public void setImage(Image image) {
			this.image = image;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			if (image == null || image.getWidth(this) <= 0 || image.getHeight(this) <= 0) {
				return;
			}

			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

			int availableW = Math.max(1, getWidth());
			int availableH = Math.max(1, getHeight());
			double scale = Math.min(availableW / (double) image.getWidth(this), availableH / (double) image.getHeight(this));
			int drawW = Math.max(1, (int) Math.round(image.getWidth(this) * scale));
			int drawH = Math.max(1, (int) Math.round(image.getHeight(this) * scale));
			int drawX = (availableW - drawW) / 2;
			int drawY = (availableH - drawH) / 2;

			g2.setColor(new Color(245, 245, 245));
			g2.fillRect(0, 0, availableW, availableH);
			g2.drawImage(image, drawX, drawY, drawW, drawH, this);
			g2.dispose();
		}
	}

	private static class AdvancedInfoStore {
		private List<String[]> festivals;
		private List<String[]> stores;
		private List<String[]> weatherProfiles;
		private List<String[]> photos;
		private List<String[]> chargers;

		public AdvancedInfoStore(File resDir) {
			festivals = readCsv(new File(resDir, "지역축제정보.csv"));
			stores = readCsv(new File(resDir, "상권정보.csv"));
			weatherProfiles = readCsv(new File(resDir, "관광지날씨.csv"));
			photos = readCsv(new File(resDir, "추천사진.csv"));
			chargers = readCsv(new File(resDir, "전기차충전소.csv"));
		}

		public List<String[]> findFestivals(TripDto trip) {
			return findByRegion(festivals, trip);
		}

		public List<String[]> findStores(TripDto trip) {
			return findByRegion(stores, trip);
		}

		public List<String[]> buildWeatherRows(TripDto trip, LocalDate startDate, LocalDate endDate) {
			String[] profile = findFirstByRegion(weatherProfiles, trip);
			if (profile == null) {
				profile = new String[] { "전국", "맑음", "8", "18", "10", "야외 활동 전 현지 예보 확인" };
			}

			List<String[]> rows = new ArrayList<String[]>();
			LocalDate date = startDate;
			int offset = 0;
			while (!date.isAfter(endDate)) {
				String condition = weatherCondition(profile[1], date.getDayOfYear() + offset);
				int minTemp = parseInt(profile, 2, 8) + temperatureShift(date.getDayOfYear(), offset);
				int maxTemp = parseInt(profile, 3, 18) + temperatureShift(date.getDayOfYear() + 3, offset);
				int rain = Math.max(0, Math.min(95, parseInt(profile, 4, 10) + rainShift(condition, offset)));
				rows.add(new String[] {
						date.toString(),
						profile[0],
						condition,
						minTemp + "도",
						maxTemp + "도",
						rain + "%",
						profile.length > 5 ? profile[5] : "현지 예보 확인"
				});
				date = date.plusDays(1);
				offset++;
			}
			return rows;
		}

		public List<String[]> findPhotos(TripDto trip) {
			return findByRegion(photos, trip);
		}

		public List<String[]> findChargers(TripDto trip) {
			return findByRegion(chargers, trip);
		}

		private List<String[]> readCsv(File file) {
			List<String[]> rows = new ArrayList<String[]>();
			if (!file.exists()) {
				return rows;
			}

			try (BufferedReader br = new BufferedReader(new FileReader(file))) {
				String line;
				boolean header = true;
				while ((line = br.readLine()) != null) {
					if (header) {
						header = false;
						continue;
					}
					line = line.trim();
					if (line.length() == 0) {
						continue;
					}
					rows.add(line.split(",", -1));
				}
			} catch (IOException e) {
				System.out.println(file.getName() + " 파일을 읽지 못했습니다: " + e.getMessage());
			}
			return rows;
		}

		private String[] findFirstByRegion(List<String[]> source, TripDto trip) {
			List<String[]> matches = findByRegion(source, trip);
			return matches.isEmpty() ? null : matches.get(0);
		}

		private List<String[]> findByRegion(List<String[]> source, TripDto trip) {
			List<String[]> matches = new ArrayList<String[]>();
			String address = nvl(trip.getStreetAddress()) + " " + nvl(trip.getLotAddress());
			String normalizedAddress = normalizeRegion(address);

			for (String[] row : source) {
				if (row.length == 0) {
					continue;
				}
				String region = row[0].trim();
				if (region.length() == 0) {
					continue;
				}
				if (address.contains(region) || normalizedAddress.contains(normalizeRegion(region))) {
					matches.add(row);
				}
			}
			return matches;
		}

		private String weatherCondition(String baseCondition, int seed) {
			String[] sunny = { "맑음", "구름조금", "맑음" };
			String[] cloudy = { "구름많음", "흐림", "구름조금" };
			String[] rainy = { "흐림", "비", "구름많음" };
			String[] source;
			if (baseCondition.contains("비")) {
				source = rainy;
			} else if (baseCondition.contains("흐") || baseCondition.contains("구름")) {
				source = cloudy;
			} else {
				source = sunny;
			}
			return source[Math.abs(seed) % source.length];
		}

		private int temperatureShift(int dayOfYear, int offset) {
			return (int) Math.round(Math.sin((dayOfYear + offset) / 3.0) * 2.0);
		}

		private int rainShift(String condition, int offset) {
			int base = condition.contains("비") ? 35 : condition.contains("흐") ? 15 : 0;
			return base + (offset % 3) * 5;
		}

		private int parseInt(String[] row, int index, int defaultValue) {
			if (row.length <= index) {
				return defaultValue;
			}
			try {
				return Integer.parseInt(row[index].trim());
			} catch (NumberFormatException e) {
				return defaultValue;
			}
		}

		private String normalizeRegion(String text) {
			return nvl(text)
					.replace("서울특별시", "서울")
					.replace("부산광역시", "부산")
					.replace("대구광역시", "대구")
					.replace("인천광역시", "인천")
					.replace("광주광역시", "광주")
					.replace("대전광역시", "대전")
					.replace("울산광역시", "울산")
					.replace("세종특별자치시", "세종")
					.replace("경기도", "경기")
					.replace("강원특별자치도", "강원")
					.replace("강원도", "강원")
					.replace("충청북도", "충북")
					.replace("충청남도", "충남")
					.replace("전북특별자치도", "전북")
					.replace("전라북도", "전북")
					.replace("전라남도", "전남")
					.replace("경상북도", "경북")
					.replace("경상남도", "경남")
					.replace("제주특별자치도", "제주");
		}

		private String nvl(String text) {
			return text == null ? "" : text;
		}
	}

	private static class StampMapPanel extends JPanel {
		private static final double MIN_LAT = 32.84757721600007;
		private static final double MAX_LAT = 39.22433502800008;
		private static final double MIN_LNG = 124.163617384;
		private static final double MAX_LNG = 132.31252188600007;
		private static final int MAP_IMAGE_WIDTH = 760;
		private static final int MAP_IMAGE_HEIGHT = 520;
		private static final int MAP_IMAGE_PAD = 34;

		private List<TripDto> trips;
		private Set<Integer> visitedTrips;
		private TripDto selectedTrip;
		private Image mapImage;
		private StampSelectionListener stampSelectionListener;
		private double zoomScale = 1.0;
		private int panX;
		private int panY;
		private int dragStartX;
		private int dragStartY;
		private int dragStartPanX;
		private int dragStartPanY;
		private boolean draggingMap;

		public StampMapPanel(List<TripDto> trips, Set<Integer> visitedTrips) {
			this.trips = trips;
			this.visitedTrips = visitedTrips;
			mapImage = new ImageIcon("img/korea_map.png").getImage();
			setBorder(BorderFactory.createTitledBorder("방문 지도"));
			setBackground(Color.WHITE);
			setToolTipText("마우스 휠로 확대/축소, 드래그로 이동, 스탬프 클릭으로 상세 조회");
			MouseAdapter mapMouseHandler = new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					dragStartX = e.getX();
					dragStartY = e.getY();
					dragStartPanX = panX;
					dragStartPanY = panY;
					draggingMap = false;
				}

				@Override
				public void mouseReleased(MouseEvent e) {
					if (draggingMap) {
						return;
					}
					TripDto clickedTrip = findStampAt(e.getX(), e.getY());
					if (clickedTrip != null && stampSelectionListener != null) {
						stampSelectionListener.onStampSelected(clickedTrip);
					}
				}

				@Override
				public void mouseDragged(MouseEvent e) {
					if (zoomScale <= 1.0) {
						return;
					}

					int dx = e.getX() - dragStartX;
					int dy = e.getY() - dragStartY;
					if (Math.abs(dx) > 3 || Math.abs(dy) > 3) {
						draggingMap = true;
					}
					panX = dragStartPanX + dx;
					panY = dragStartPanY + dy;
					clampPan();
					repaint();
				}
			};
			addMouseListener(mapMouseHandler);
			addMouseMotionListener(mapMouseHandler);
			addMouseWheelListener(e -> zoomAt(e.getX(), e.getY(), e.getPreciseWheelRotation()));
		}

		public void setStampSelectionListener(StampSelectionListener stampSelectionListener) {
			this.stampSelectionListener = stampSelectionListener;
		}

		public void setSelectedTrip(TripDto selectedTrip) {
			this.selectedTrip = selectedTrip;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);

			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			int[] mapBounds = getMapBounds();
			int mapX = mapBounds[0];
			int mapY = mapBounds[1];
			int mapW = mapBounds[2];
			int mapH = mapBounds[3];

			drawMapImage(g2, mapX, mapY, mapW, mapH);
			drawSelectedTrip(g2, mapX, mapY, mapW, mapH);
			drawVisitedStamps(g2, mapX, mapY, mapW, mapH);
			drawGuideText(g2);

			g2.dispose();
		}

		private int[] getMapBounds() {
			int[] baseBounds = getBaseMapBounds();
			int baseX = baseBounds[0];
			int baseY = baseBounds[1];
			int baseW = baseBounds[2];
			int baseH = baseBounds[3];
			int drawW = (int) Math.round(baseW * zoomScale);
			int drawH = (int) Math.round(baseH * zoomScale);
			int drawX = baseX + (baseW - drawW) / 2 + panX;
			int drawY = baseY + (baseH - drawH) / 2 + panY;
			return new int[] { drawX, drawY, drawW, drawH };
		}

		private int[] getBaseMapBounds() {
			int availableX = 14;
			int availableY = 24;
			int availableW = Math.max(100, getWidth() - 28);
			int availableH = Math.max(100, getHeight() - 50);
			double imageRatio = MAP_IMAGE_WIDTH / (double) MAP_IMAGE_HEIGHT;
			double availableRatio = availableW / (double) availableH;

			int drawW;
			int drawH;
			if (availableRatio > imageRatio) {
				drawH = availableH;
				drawW = (int) Math.round(drawH * imageRatio);
			} else {
				drawW = availableW;
				drawH = (int) Math.round(drawW / imageRatio);
			}

			int drawX = availableX + (availableW - drawW) / 2;
			int drawY = availableY + (availableH - drawH) / 2;
			return new int[] { drawX, drawY, drawW, drawH };
		}

		private void zoomAt(int mouseX, int mouseY, double wheelRotation) {
			if (wheelRotation == 0) {
				return;
			}

			int[] oldBounds = getMapBounds();
			double oldZoom = zoomScale;
			double newZoom = clamp(oldZoom * Math.pow(1.15, -wheelRotation), 1.0, 4.0);
			if (Math.abs(newZoom - oldZoom) < 0.001) {
				return;
			}

			double ratioX = (mouseX - oldBounds[0]) / (double) oldBounds[2];
			double ratioY = (mouseY - oldBounds[1]) / (double) oldBounds[3];

			zoomScale = newZoom;
			int[] baseBounds = getBaseMapBounds();
			int newW = (int) Math.round(baseBounds[2] * zoomScale);
			int newH = (int) Math.round(baseBounds[3] * zoomScale);
			panX = (int) Math.round(mouseX - ratioX * newW - baseBounds[0] - (baseBounds[2] - newW) / 2.0);
			panY = (int) Math.round(mouseY - ratioY * newH - baseBounds[1] - (baseBounds[3] - newH) / 2.0);

			clampPan();
			repaint();
		}

		private void clampPan() {
			if (zoomScale <= 1.0) {
				zoomScale = 1.0;
				panX = 0;
				panY = 0;
				return;
			}

			int[] baseBounds = getBaseMapBounds();
			int maxPanX = Math.max(0, ((int) Math.round(baseBounds[2] * zoomScale) - baseBounds[2]) / 2);
			int maxPanY = Math.max(0, ((int) Math.round(baseBounds[3] * zoomScale) - baseBounds[3]) / 2);
			panX = clamp(panX, -maxPanX, maxPanX);
			panY = clamp(panY, -maxPanY, maxPanY);
		}

		private double clamp(double value, double min, double max) {
			return Math.max(min, Math.min(max, value));
		}

		private int clamp(int value, int min, int max) {
			return Math.max(min, Math.min(max, value));
		}

		private void drawMapImage(Graphics2D g2, int x, int y, int w, int h) {
			if (mapImage.getWidth(this) > 0) {
				g2.drawImage(mapImage, x, y, w, h, this);
				return;
			}

			g2.setColor(new Color(231, 242, 248));
			g2.fillRoundRect(x, y, w, h, 14, 14);
			g2.setColor(new Color(136, 184, 156));
			g2.drawRoundRect(x, y, w, h, 14, 14);
		}

		private void drawSelectedTrip(Graphics2D g2, int x, int y, int w, int h) {
			if (selectedTrip == null || visitedTrips.contains(selectedTrip.getNum())) {
				return;
			}

			double[] coordinate = getCoordinate(selectedTrip);
			if (coordinate == null) {
				return;
			}

			int px = toX(coordinate[1], x, w);
			int py = toY(coordinate[0], y, h);
			g2.setColor(new Color(45, 112, 190));
			g2.fillOval(px - 5, py - 5, 10, 10);
			g2.setColor(Color.WHITE);
			g2.fillOval(px - 2, py - 2, 4, 4);
		}

		private void drawVisitedStamps(Graphics2D g2, int x, int y, int w, int h) {
			for (TripDto trip : trips) {
				if (!visitedTrips.contains(trip.getNum())) {
					continue;
				}

				double[] coordinate = getCoordinate(trip);
				if (coordinate == null) {
					continue;
				}

				int px = toX(coordinate[1], x, w);
				int py = toY(coordinate[0], y, h);
				drawStamp(g2, px, py);
			}
		}

		private TripDto findStampAt(int mouseX, int mouseY) {
			int[] mapBounds = getMapBounds();
			TripDto closestTrip = null;
			double closestDistance = Double.MAX_VALUE;
			int hitRadius = 17;

			for (TripDto trip : trips) {
				if (!visitedTrips.contains(trip.getNum())) {
					continue;
				}

				double[] coordinate = getCoordinate(trip);
				if (coordinate == null) {
					continue;
				}

				int px = toX(coordinate[1], mapBounds[0], mapBounds[2]);
				int py = toY(coordinate[0], mapBounds[1], mapBounds[3]);
				double distance = Math.hypot(mouseX - px, mouseY - py);
				if (distance <= hitRadius && distance < closestDistance) {
					closestDistance = distance;
					closestTrip = trip;
				}
			}

			return closestTrip;
		}

		private void drawStamp(Graphics2D g2, int x, int y) {
			g2.setStroke(new BasicStroke(2f));
			g2.setColor(new Color(198, 46, 46));
			g2.fillOval(x - 13, y - 13, 26, 26);
			g2.setColor(Color.WHITE);
			g2.drawOval(x - 9, y - 9, 18, 18);
			g2.setFont(new Font("Dialog", Font.BOLD, 9));
			FontMetrics fm = g2.getFontMetrics();
			String text = "방문";
			g2.drawString(text, x - fm.stringWidth(text) / 2, y + 3);
		}

		private void drawGuideText(Graphics2D g2) {
			g2.setFont(new Font("Dialog", Font.PLAIN, 12));
			g2.setColor(new Color(80, 80, 80));
			String text = visitedTrips.isEmpty() ? "다녀온 곳을 체크하면 지도에 스탬프가 찍힙니다."
					: "휠: 확대/축소  드래그: 이동  스탬프 클릭: 관광지 정보";
			g2.drawString(text, 18, getHeight() - 12);
		}

		private int toX(double lng, int x, int w) {
			double imageX = MAP_IMAGE_PAD + (lng - MIN_LNG) / (MAX_LNG - MIN_LNG)
					* (MAP_IMAGE_WIDTH - MAP_IMAGE_PAD * 2);
			return x + (int) Math.round(imageX / MAP_IMAGE_WIDTH * w);
		}

		private int toY(double lat, int y, int h) {
			double imageY = MAP_IMAGE_PAD + (MAX_LAT - lat) / (MAX_LAT - MIN_LAT)
					* (MAP_IMAGE_HEIGHT - MAP_IMAGE_PAD * 2);
			return y + (int) Math.round(imageY / MAP_IMAGE_HEIGHT * h);
		}

		private double[] getCoordinate(TripDto trip) {
			if (trip.getLat() >= MIN_LAT && trip.getLat() <= MAX_LAT && trip.getLng() >= MIN_LNG
					&& trip.getLng() <= MAX_LNG) {
				return new double[] { trip.getLat(), trip.getLng() };
			}
			return estimateCoordinate(trip);
		}

		private double[] estimateCoordinate(TripDto trip) {
			String address = (nvl(trip.getStreetAddress()) + " " + nvl(trip.getLotAddress()));
			if (address.contains("서울")) return new double[] { 37.5665, 126.9780 };
			if (address.contains("부산")) return new double[] { 35.1796, 129.0756 };
			if (address.contains("대구")) return new double[] { 35.8714, 128.6014 };
			if (address.contains("인천")) return new double[] { 37.4563, 126.7052 };
			if (address.contains("광주")) return new double[] { 35.1595, 126.8526 };
			if (address.contains("대전")) return new double[] { 36.3504, 127.3845 };
			if (address.contains("울산")) return new double[] { 35.5384, 129.3114 };
			if (address.contains("세종")) return new double[] { 36.4801, 127.2890 };
			if (address.contains("경기")) return new double[] { 37.4138, 127.5183 };
			if (address.contains("강원")) return new double[] { 37.8228, 128.1555 };
			if (address.contains("충청북도") || address.contains("충북")) return new double[] { 36.8000, 127.7000 };
			if (address.contains("충청남도") || address.contains("충남")) return new double[] { 36.5184, 126.8000 };
			if (address.contains("전라북도") || address.contains("전북")) return new double[] { 35.7175, 127.1530 };
			if (address.contains("전라남도") || address.contains("전남")) return new double[] { 34.8679, 126.9910 };
			if (address.contains("경상북도") || address.contains("경북")) return new double[] { 36.4919, 128.8889 };
			if (address.contains("경상남도") || address.contains("경남")) return new double[] { 35.4606, 128.2132 };
			if (address.contains("제주")) return new double[] { 33.4996, 126.5312 };
			return null;
		}

		private String nvl(String text) {
			return text == null ? "" : text;
		}

		private interface StampSelectionListener {
			void onStampSelected(TripDto trip);
		}
	}
}
