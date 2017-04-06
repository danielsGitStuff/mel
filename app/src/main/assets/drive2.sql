CREATE TRIGGER IF NOT EXISTS stamp2
AFTER UPDATE ON waste
BEGIN
  UPDATE waste
  SET deleted = current_timestamp
  WHERE hash = NEW.hash;
END;