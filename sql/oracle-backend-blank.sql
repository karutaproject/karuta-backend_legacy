--------------------------------------------
-- WARNING -- GRANT EXECUTE ON DBMS_CRYPTO TO karuta;
--------------------------------------------

--
CREATE OR REPLACE 
FUNCTION crypt(pw IN VARCHAR2) RETURN RAW DETERMINISTIC AS
BEGIN
  return dbms_crypto.hash(utl_raw.cast_to_raw(pw), dbms_crypto.hash_sh1);
END crypt;
/
CREATE OR REPLACE 
FUNCTION uuid2bin(uuid IN CHAR) RETURN RAW DETERMINISTIC AS
BEGIN
  return hextoraw(replace(uuid, '-',''));
END uuid2bin;
/
CREATE OR REPLACE 
FUNCTION bin2uuid(bin IN RAW) RETURN CHAR DETERMINISTIC AS
BEGIN
    IF bin IS NULL THEN
        return NULL;
    ELSE
	DECLARE HexData char(32 char) := rawtohex(bin);
	BEGIN
	return
	    LOWER(substr(HexData, 1, 8)
	    || '-'
	    || substr(HexData, 9, 4)
	    || '-'
	    || substr(HexData, 13, 4)
	    || '-'
	    || substr(HexData, 17, 4)
	    || '-'
	    || substr(HexData, 21, 12));
	END;
    END IF;
	
END bin2uuid;
/

create or replace 
procedure create_or_empty_table (v_table_name varchar2, v_sql varchar2)
is
begin
	declare
		v_counter number;
	begin
	  SELECT count(*) INTO v_counter FROM user_tables WHERE table_name = upper(v_table_name);
		IF(v_counter <= 0)
	 	THEN
			execute immediate v_sql;
		ELSE
			execute immediate 'truncate table '||v_table_name;
		END IF;
	end;
end;
/
--

drop table annotation cascade constraints purge;
CREATE TABLE annotation (
  nodeid RAW(16) NOT NULL,
  rank NUMBER(10,0) NOT NULL,
  text CLOB,
  c_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  a_user VARCHAR2(255 CHAR) NOT NULL,
  wad_identifier VARCHAR2(45 CHAR) DEFAULT NULL,
  CONSTRAINT annotation_PK PRIMARY KEY (nodeid,rank)
);

drop table credential cascade constraints purge;
CREATE TABLE credential (
  userid NUMBER(19,0) NOT NULL,
  login VARCHAR2(255 CHAR) NOT NULL,
  can_substitute NUMBER(1) DEFAULT '0' NOT NULL,
  is_admin NUMBER(10,0) DEFAULT '0' NOT NULL,
  is_designer NUMBER(10,0) DEFAULT '0' NOT NULL,
  active NUMBER(10,0) DEFAULT '1' NOT NULL,
  display_firstname VARCHAR2(255 CHAR) NOT NULL,
  display_lastname VARCHAR2(255 CHAR) DEFAULT NULL,
  email VARCHAR2(255 CHAR) DEFAULT NULL,
  password RAW(20) NOT NULL,
  token VARCHAR2(255 CHAR) DEFAULT NULL,
  c_date NUMBER(19,0) DEFAULT NULL,
  CONSTRAINT credential_PK PRIMARY KEY (userid),
  CONSTRAINT credential_UK_login UNIQUE (login)
);
--------------------------------------------------------
--  DDL for Trigger credential_TRG
--------------------------------------------------------
drop sequence credential_SEQ;
CREATE SEQUENCE credential_SEQ START WITH 2 INCREMENT BY 1;
CREATE OR REPLACE 
TRIGGER credential_TRG
BEFORE INSERT ON credential
FOR EACH ROW
DECLARE
    MAX_ID NUMBER;
    CUR_SEQ NUMBER;
BEGIN
    IF :NEW.userid IS NULL THEN
        SELECT credential_SEQ.NEXTVAL INTO :NEW.userid FROM DUAL;
    ELSE
        SELECT GREATEST(NVL(MAX(userid),0), :NEW.userid) INTO MAX_ID FROM credential;
        SELECT credential_SEQ.NEXTVAL INTO CUR_SEQ FROM DUAL;
        WHILE CUR_SEQ < MAX_ID
        LOOP
            SELECT credential_SEQ.NEXTVAL INTO CUR_SEQ FROM DUAL;
        END LOOP;
    END IF;
END;
/
ALTER TRIGGER credential_TRG ENABLE;

drop table credential_substitution cascade constraints purge;
CREATE TABLE credential_substitution (
userid NUMBER(19,0) NOT NULL,
id NUMBER(19,0) NOT NULL,
type VARCHAR2(5 CHAR) DEFAULT 'USER' CHECK( type IN ('USER','GROUP') ),
CONSTRAINT credential_substitution_PK PRIMARY KEY (userid,id,type)
);

drop table credential_group cascade constraints purge;
CREATE TABLE credential_group (
  cg NUMBER(19,0) NOT NULL,
  CONSTRAINT credential_group_PK PRIMARY KEY (cg)
);

drop table credential_group_members cascade constraints purge;
CREATE TABLE credential_group_members (
  cg NUMBER(19,0) NOT NULL,
  userid NUMBER(19,0) NOT NULL,
  CONSTRAINT credential_group_members_PK PRIMARY KEY (cg,userid)
);

drop table data_table cascade constraints purge;
CREATE TABLE data_table (
  id RAW(16) NOT NULL,
  owner NUMBER(19,0) NOT NULL,
  creator NUMBER(19,0) NOT NULL,
  type VARCHAR2(255 CHAR) NOT NULL,
  mimetype VARCHAR2(255 CHAR) DEFAULT NULL,
  filename VARCHAR2(255 CHAR) DEFAULT NULL,
  c_date NUMBER(19,0) DEFAULT NULL,
  data BLOB,
  CONSTRAINT data_table_PK PRIMARY KEY (id)
);

drop table definition_info cascade constraints purge;
CREATE TABLE definition_info (
  def_id NUMBER(19,0) NOT NULL,
  label VARCHAR2(255 CHAR) DEFAULT 'Nouveau Type' NOT NULL,
  CONSTRAINT definition_info_PK PRIMARY KEY (def_id)
);
--------------------------------------------------------
--  DDL for Trigger definition_info_TRG
--------------------------------------------------------
drop sequence definition_info_SEQ;
CREATE SEQUENCE definition_info_SEQ START WITH 1 INCREMENT BY 1;
CREATE OR REPLACE 
TRIGGER definition_info_TRG
BEFORE INSERT ON definition_info
FOR EACH ROW
DECLARE
    MAX_ID NUMBER;
    CUR_SEQ NUMBER;
BEGIN
    IF :NEW.def_id IS NULL THEN
        SELECT definition_info_SEQ.NEXTVAL INTO :NEW.def_id FROM DUAL;
    ELSE
        SELECT GREATEST(NVL(MAX(def_id),0), :NEW.def_id) INTO MAX_ID FROM definition_info;
        SELECT definition_info_SEQ.NEXTVAL INTO CUR_SEQ FROM DUAL;
        WHILE CUR_SEQ < MAX_ID
        LOOP
            SELECT definition_info_SEQ.NEXTVAL INTO CUR_SEQ FROM DUAL;
        END LOOP;
    END IF;
END;
/
ALTER TRIGGER definition_info_TRG ENABLE;

drop table definition_type cascade constraints purge;
CREATE TABLE definition_type (
  node_id NUMBER(19,0) NOT NULL,
  def_id NUMBER(19,0) NOT NULL,
  asm_type varchar(50) DEFAULT NULL,
  xsi_type varchar(50) DEFAULT NULL,
  parent_node NUMBER(19,0) DEFAULT NULL,
  node_data CLOB,
  instance_rule NUMBER(19,0) NOT NULL,
  CONSTRAINT definition_type_PK PRIMARY KEY (node_id,def_id)
);
--------------------------------------------------------
--  DDL for Trigger definition_type_TRG
--------------------------------------------------------
drop sequence definition_type_SEQ;
CREATE SEQUENCE definition_type_SEQ START WITH 1 INCREMENT BY 1;
CREATE OR REPLACE 
TRIGGER definition_type_TRG
BEFORE INSERT ON definition_type
FOR EACH ROW
DECLARE
    MAX_ID NUMBER;
    CUR_SEQ NUMBER;
BEGIN
    IF :NEW.node_id IS NULL THEN
        SELECT definition_type_SEQ.NEXTVAL INTO :NEW.node_id FROM DUAL;
    ELSE
        SELECT GREATEST(NVL(MAX(node_id),0), :NEW.node_id) INTO MAX_ID FROM definition_type;
        SELECT definition_type_SEQ.NEXTVAL INTO CUR_SEQ FROM DUAL;
        WHILE CUR_SEQ < MAX_ID
        LOOP
            SELECT definition_type_SEQ.NEXTVAL INTO CUR_SEQ FROM DUAL;
        END LOOP;
    END IF;
END;
/
ALTER TRIGGER definition_type_TRG ENABLE;

drop table file_table cascade constraints purge;
CREATE TABLE file_table (
  node_uuid RAW(16) NOT NULL,
  lang VARCHAR2(10 CHAR) DEFAULT NULL,
  name VARCHAR2(254 CHAR) NOT NULL,
  type VARCHAR2(254 CHAR) DEFAULT NULL,
  extension VARCHAR2(10 CHAR) NOT NULL,
  filesize NUMBER(10,0) DEFAULT NULL,
  filecontent BLOB,
  modif_user_id NUMBER(10,0) NOT NULL,
  modif_date TIMESTAMP(3) NOT NULL,
  CONSTRAINT file_table_PK PRIMARY KEY (node_uuid,lang)
);

drop table group_group cascade constraints purge;
CREATE TABLE group_group (
  gid NUMBER(19,0) NOT NULL,
  child_gid NUMBER(19,0) NOT NULL,
  CONSTRAINT group_group_PK PRIMARY KEY (gid,child_gid)
);

drop table group_info cascade constraints purge;
CREATE TABLE group_info (
  gid NUMBER(19,0) NOT NULL,
  grid NUMBER(19,0) DEFAULT NULL,
  owner NUMBER(19,0) NOT NULL,
  label VARCHAR2(255 CHAR) DEFAULT 'Nouveau groupe' NOT NULL,
  CONSTRAINT group_info_PK PRIMARY KEY (gid)
);
--------------------------------------------------------
--  DDL for Trigger group_info_TRG
--------------------------------------------------------
drop sequence group_info_SEQ;
CREATE SEQUENCE group_info_SEQ START WITH 3 INCREMENT BY 1;
CREATE OR REPLACE 
TRIGGER group_info_TRG
BEFORE INSERT ON group_info
FOR EACH ROW
DECLARE
    MAX_ID NUMBER;
    CUR_SEQ NUMBER;
BEGIN
    IF :NEW.gid IS NULL THEN
        SELECT group_info_SEQ.NEXTVAL INTO :NEW.gid FROM DUAL;
    ELSE
        SELECT GREATEST(NVL(MAX(gid),0), :NEW.gid) INTO MAX_ID FROM group_info;
        SELECT group_info_SEQ.NEXTVAL INTO CUR_SEQ FROM DUAL;
        WHILE CUR_SEQ < MAX_ID
        LOOP
            SELECT group_info_SEQ.NEXTVAL INTO CUR_SEQ FROM DUAL;
        END LOOP;
    END IF;
END;
/
ALTER TRIGGER group_info_TRG ENABLE;

drop table group_right_info cascade constraints purge;
CREATE TABLE group_right_info (
  grid NUMBER(19,0) NOT NULL,
  owner NUMBER(19,0) NOT NULL,
  label VARCHAR2(255 CHAR) DEFAULT 'Nouveau groupe' NOT NULL,
  change_rights NUMBER(1) DEFAULT '0' NOT NULL,
  portfolio_id RAW(16) DEFAULT NULL,
  CONSTRAINT group_right_info_PK PRIMARY KEY (grid)
);
--------------------------------------------------------
--  DDL for Trigger group_right_info_TRG
--------------------------------------------------------
drop sequence group_right_info_SEQ;
CREATE SEQUENCE group_right_info_SEQ START WITH 3 INCREMENT BY 1;
CREATE OR REPLACE 
TRIGGER group_right_info_TRG
BEFORE INSERT ON group_right_info
FOR EACH ROW
DECLARE
    MAX_ID NUMBER;
    CUR_SEQ NUMBER;
BEGIN
    IF :NEW.grid IS NULL THEN
        SELECT group_right_info_SEQ.NEXTVAL INTO :NEW.grid FROM DUAL;
    ELSE
        SELECT GREATEST(NVL(MAX(grid),0), :NEW.grid) INTO MAX_ID FROM group_right_info;
        SELECT group_right_info_SEQ.NEXTVAL INTO CUR_SEQ FROM DUAL;
        WHILE CUR_SEQ < MAX_ID
        LOOP
            SELECT group_right_info_SEQ.NEXTVAL INTO CUR_SEQ FROM DUAL;
        END LOOP;
    END IF;
END;
/
ALTER TRIGGER group_right_info_TRG ENABLE;

drop table group_rights cascade constraints purge;
CREATE TABLE group_rights (
  grid NUMBER(19,0) NOT NULL,
  id RAW(16) NOT NULL,
  RD NUMBER(1) DEFAULT '1' NOT NULL,
  WR NUMBER(1) DEFAULT '0' NOT NULL,
  DL NUMBER(1) DEFAULT '0' NOT NULL,
  SB NUMBER(1) DEFAULT '0' NOT NULL,
  AD NUMBER(1) DEFAULT '0' NOT NULL,
  types_id VARCHAR2(2000 CHAR),
  rules_id VARCHAR2(2000 CHAR),
  notify_roles CLOB DEFAULT NULL,
  CONSTRAINT group_rights_PK PRIMARY KEY (grid,id)
);

drop table group_user cascade constraints purge;
CREATE TABLE group_user (
  gid NUMBER(19,0) NOT NULL,
  userid NUMBER(19,0) NOT NULL,
  CONSTRAINT group_user_PK PRIMARY KEY (gid,userid)
);
CREATE INDEX gid ON group_user (gid ASC);

drop table log_table cascade constraints purge;
CREATE TABLE log_table (
  log_id NUMBER(10,0) NOT NULL,
  log_date TIMESTAMP(3) NOT NULL,
  log_url VARCHAR2(255 CHAR) NOT NULL,
  log_method VARCHAR2(50 CHAR) NOT NULL,
  log_headers CLOB NOT NULL,
  log_in_body CLOB,
  log_out_body CLOB,
  log_code NUMBER(10,0) NOT NULL,
  CONSTRAINT log_table_PK PRIMARY KEY (log_id)
);
--------------------------------------------------------
--  DDL for Trigger log_table_TRG
--------------------------------------------------------
drop sequence log_table_SEQ;
CREATE SEQUENCE log_table_SEQ START WITH 1 INCREMENT BY 1;
CREATE OR REPLACE 
TRIGGER log_table_TRG
BEFORE INSERT ON log_table
FOR EACH ROW
DECLARE
    MAX_ID NUMBER;
    CUR_SEQ NUMBER;
BEGIN
    IF :NEW.log_id IS NULL THEN
        SELECT log_table_SEQ.NEXTVAL INTO :NEW.log_id FROM DUAL;
    ELSE
        SELECT GREATEST(NVL(MAX(log_id),0), :NEW.log_id) INTO MAX_ID FROM log_table;
        SELECT log_table_SEQ.NEXTVAL INTO CUR_SEQ FROM DUAL;
        WHILE CUR_SEQ < MAX_ID
        LOOP
            SELECT log_table_SEQ.NEXTVAL INTO CUR_SEQ FROM DUAL;
        END LOOP;
    END IF;
END;
/
ALTER TRIGGER log_table_TRG ENABLE;

drop table node cascade constraints purge;
CREATE TABLE node (
  node_uuid RAW(16) NOT NULL,
  node_parent_uuid RAW(16) DEFAULT NULL,
  node_children_uuid CLOB,
  node_order NUMBER(10,0) NOT NULL,
  metadata CLOB DEFAULT ' ',
  metadata_wad CLOB DEFAULT ' ',
  metadata_epm CLOB DEFAULT ' ',
  res_node_uuid RAW(16) DEFAULT NULL,
  res_res_node_uuid RAW(16) DEFAULT NULL,
  res_context_node_uuid RAW(16) DEFAULT NULL,
  shared_res NUMBER(1) NOT NULL,
  shared_node NUMBER(1) NOT NULL,
  shared_node_res NUMBER(1) NOT NULL,
  shared_res_uuid RAW(16) DEFAULT NULL,
  shared_node_uuid RAW(16) DEFAULT NULL,
  shared_node_res_uuid RAW(16) DEFAULT NULL,
  asm_type VARCHAR2(50 CHAR) DEFAULT NULL,
  xsi_type VARCHAR2(50 CHAR) DEFAULT NULL,
  semtag VARCHAR2(250 CHAR) DEFAULT NULL,
  semantictag VARCHAR2(250 CHAR) DEFAULT NULL,
  label VARCHAR2(250 CHAR) DEFAULT NULL,
  code VARCHAR2(45 CHAR) DEFAULT NULL,
  descr VARCHAR2(250 CHAR) DEFAULT NULL,
  format VARCHAR2(30 CHAR) DEFAULT NULL,
  modif_user_id NUMBER(10,0) NOT NULL,
  modif_date TIMESTAMP DEFAULT NULL,
  portfolio_id RAW(16) DEFAULT NULL,
  CONSTRAINT node_PK PRIMARY KEY (node_uuid)
);
CREATE INDEX portfolio_id ON node (portfolio_id ASC);
CREATE INDEX node_parent_uuid ON node (node_parent_uuid ASC);
CREATE INDEX node_order ON node (node_order ASC);
CREATE INDEX asm_type ON node (asm_type ASC);
CREATE INDEX res_node_uuid ON node (res_node_uuid ASC);
CREATE INDEX res_res_node_uuid ON node (res_res_node_uuid ASC);
CREATE INDEX res_context_node_uuid ON node (res_context_node_uuid ASC);

drop table portfolio cascade constraints purge;
CREATE TABLE portfolio (
  portfolio_id RAW(16) NOT NULL,
  root_node_uuid RAW(16) DEFAULT NULL,
  user_id NUMBER(10,0) NOT NULL,
  model_id RAW(16) DEFAULT NULL,
  modif_user_id NUMBER(10,0) NOT NULL,
  modif_date TIMESTAMP DEFAULT NULL,
  active NUMBER(1) DEFAULT '1' NOT NULL,
  CONSTRAINT portfolio_PK PRIMARY KEY (portfolio_id)
);
CREATE INDEX root_node_uuid ON portfolio (root_node_uuid ASC);

drop table portfolio_group cascade constraints purge;
CREATE TABLE portfolio_group (
  owner NUMBER(19,0) NOT NULL,
  portfolio_id RAW(16) NOT NULL,
  group_name VARCHAR2(255 CHAR) NOT NULL,
  CONSTRAINT portfolio_group_PK PRIMARY KEY (portfolio_id,group_name)
);

drop table portfolio_group_members cascade constraints purge;
CREATE TABLE portfolio_group_members (
  pg NUMBER(19,0) NOT NULL,
  portfolio_id RAW(16) DEFAULT '00000000000000000000000000000000',
  CONSTRAINT portfolio_group_members_PK PRIMARY KEY (pg,portfolio_id)
);

drop table resource_table cascade constraints purge;
CREATE TABLE resource_table (
  node_uuid RAW(16) NOT NULL,
  xsi_type VARCHAR2(50 CHAR) DEFAULT NULL,
  content CLOB,
  user_id NUMBER(10,0) DEFAULT NULL,
  modif_user_id NUMBER(10,0) NOT NULL,
  modif_date TIMESTAMP DEFAULT NULL,
  CONSTRAINT resource_table_PK PRIMARY KEY (node_uuid)
);

drop table rule_info cascade constraints purge;
CREATE TABLE rule_info (
  rule_id NUMBER(19,0) NOT NULL,
  label VARCHAR2(255 CHAR) DEFAULT 'Nouvelle Commande' NOT NULL,
  CONSTRAINT rule_info_PK PRIMARY KEY (rule_id)
);
--------------------------------------------------------
--  DDL for Trigger rule_info_TRG
--------------------------------------------------------
drop sequence rule_info_SEQ;
CREATE SEQUENCE rule_info_SEQ START WITH 1 INCREMENT BY 1;
CREATE OR REPLACE 
TRIGGER rule_info_TRG
BEFORE INSERT ON rule_info
FOR EACH ROW
DECLARE
    MAX_ID NUMBER;
    CUR_SEQ NUMBER;
BEGIN
    IF :NEW.rule_id IS NULL THEN
        SELECT rule_info_SEQ.NEXTVAL INTO :NEW.rule_id FROM DUAL;
    ELSE
        SELECT GREATEST(NVL(MAX(rule_id),0), :NEW.rule_id) INTO MAX_ID FROM rule_info;
        SELECT rule_info_SEQ.NEXTVAL INTO CUR_SEQ FROM DUAL;
        WHILE CUR_SEQ < MAX_ID
        LOOP
            SELECT rule_info_SEQ.NEXTVAL INTO CUR_SEQ FROM DUAL;
        END LOOP;
    END IF;
END;
/
ALTER TRIGGER rule_info_TRG ENABLE;

drop table rule_table cascade constraints purge;
CREATE TABLE rule_table (
  rule_id NUMBER(19,0) NOT NULL,
  role VARCHAR2(255 CHAR) NOT NULL,
  RD NUMBER(1) DEFAULT '1' NOT NULL,
  WR NUMBER(1) DEFAULT '0' NOT NULL,
  DL NUMBER(1) DEFAULT '0' NOT NULL,
  AD NUMBER(1) DEFAULT '0' NOT NULL,
  types_id VARCHAR2(2000 CHAR),
  rules_id VARCHAR2(2000 CHAR),
  CONSTRAINT rule_table_PK PRIMARY KEY (rule_id,role)
);
--
drop table complete_share cascade constraints purge;
CREATE TABLE complete_share (
userid NUMBER(19,0) NOT NULL,
portfolio_id RAW(16) NOT NULL,
CONSTRAINT complete_share_PK PRIMARY KEY (userid,portfolio_id));

--
ALTER TABLE group_rights
ADD CONSTRAINT group_rights_group_right__FK1 FOREIGN KEY(grid) REFERENCES group_right_info(grid)
ON DELETE CASCADE ENABLE;

ALTER TABLE group_info
ADD CONSTRAINT group_info_group_right__FK1 FOREIGN KEY(grid) REFERENCES group_right_info(grid)
ON DELETE CASCADE ENABLE;

ALTER TABLE group_user
ADD CONSTRAINT group_user_group_info_FK1 FOREIGN KEY(gid) REFERENCES group_info(gid)
ON DELETE CASCADE ENABLE;

ALTER TABLE definition_type
ADD CONSTRAINT definition_type_definition_FK1 FOREIGN KEY(def_id) REFERENCES definition_info(def_id)
ON DELETE CASCADE ENABLE;

ALTER TABLE rule_table
ADD CONSTRAINT rule_table_rule_info_FK1 FOREIGN KEY(rule_id) REFERENCES rule_info(rule_id)
ON DELETE CASCADE ENABLE;

--
insert into credential(userid,login,can_substitute, is_admin,is_designer,active, display_firstname, display_lastname, email,password,token,c_date) values('1','root','0', '1','0', '1', 'root', '',NULL, crypt('mati'), NULL, NULL);
--
commit;