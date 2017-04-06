CREATE TRIGGER IF NOT EXISTS stamp1
AFTER INSERT ON waste
BEGIN
  UPDATE waste
  SET deleted = current_timestamp
  WHERE hash = NEW.hash;
END;