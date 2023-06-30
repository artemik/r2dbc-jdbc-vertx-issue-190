-- Create a mock table
CREATE TABLE companies (
  id SERIAL PRIMARY KEY,
  company_id NUMERIC(19),
  name VARCHAR(255)
);

INSERT INTO companies (company_id, name) VALUES (10, 'John Doe');
INSERT INTO companies (company_id, name) VALUES (11, 'Jane Smith');

CREATE TABLE insert_table (
  id SERIAL PRIMARY KEY,
  uuid VARCHAR(255)
);
