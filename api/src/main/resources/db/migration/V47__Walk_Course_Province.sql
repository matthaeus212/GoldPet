ALTER TABLE walk_courses ADD COLUMN province VARCHAR(50);

UPDATE walk_courses SET province = CASE
  WHEN region LIKE '서울%' THEN '서울'
  WHEN region LIKE '경기%' THEN '경기'
  WHEN region LIKE '강원%' THEN '강원'
  WHEN region LIKE '경상북%' THEN '경북'
  WHEN region LIKE '경상남%' THEN '경남'
  WHEN region LIKE '광주%' THEN '광주'
  WHEN region LIKE '대구%' THEN '대구'
  WHEN region LIKE '대전%' THEN '대전'
  WHEN region LIKE '부산%' THEN '부산'
  WHEN region LIKE '세종%' THEN '세종'
  WHEN region LIKE '울산%' THEN '울산'
  WHEN region LIKE '인천%' THEN '인천'
  WHEN region LIKE '전라남%' THEN '전남'
  WHEN region LIKE '전북%' OR region LIKE '전라북%' THEN '전북'
  WHEN region LIKE '제주%' THEN '제주'
  WHEN region LIKE '충청남%' THEN '충남'
  WHEN region LIKE '충청북%' THEN '충북'
  ELSE NULL
END;

CREATE INDEX idx_walk_courses_province ON walk_courses (province);
