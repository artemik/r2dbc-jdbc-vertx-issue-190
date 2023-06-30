-- Create a mock table
CREATE TABLE companies(
    company_id SERIAL PRIMARY KEY,
    company_name VARCHAR(255)
);

-- Some sample data generation.
INSERT INTO companies(company_name)
    SELECT md5(random()::text) FROM generate_series(1, 100000);

CREATE TABLE insert_table (
  id SERIAL PRIMARY KEY,
  uuid VARCHAR(255)
);
