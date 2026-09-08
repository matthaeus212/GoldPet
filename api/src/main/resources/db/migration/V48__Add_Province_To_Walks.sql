ALTER TABLE walks ADD COLUMN province VARCHAR(50);

UPDATE walks SET province = CASE
  WHEN start_address LIKE '서울%' THEN '서울'
  WHEN start_address LIKE '경기%' THEN '경기'
  WHEN start_address LIKE '강원%' THEN '강원'
  WHEN start_address LIKE '경상북%' THEN '경북'
  WHEN start_address LIKE '경상남%' THEN '경남'
  WHEN start_address LIKE '광주%' THEN '광주'
  WHEN start_address LIKE '대구%' THEN '대구'
  WHEN start_address LIKE '대전%' THEN '대전'
  WHEN start_address LIKE '부산%' THEN '부산'
  WHEN start_address LIKE '세종%' THEN '세종'
  WHEN start_address LIKE '울산%' THEN '울산'
  WHEN start_address LIKE '인천%' THEN '인천'
  WHEN start_address LIKE '전라남%' THEN '전남'
  WHEN start_address LIKE '전북%' OR start_address LIKE '전라북%' THEN '전북'
  WHEN start_address LIKE '제주%' THEN '제주'
  WHEN start_address LIKE '충청남%' THEN '충남'
  WHEN start_address LIKE '충청북%' THEN '충북'
  ELSE NULL
END
WHERE start_address IS NOT NULL;

CREATE INDEX idx_walks_province ON walks(province);
