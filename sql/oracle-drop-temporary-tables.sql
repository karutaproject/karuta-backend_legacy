truncate table t_struc;
drop table t_struc cascade constraints purge;
truncate table t_struc_2;
drop table t_struc_2 cascade constraints purge;
truncate table t_struc_parentid;
drop table t_struc_parentid cascade constraints purge;
truncate table t_struc_parentid_2;
drop table t_struc_parentid_2 cascade constraints purge;
truncate table t_struc_node_resids;
drop table t_struc_node_resids cascade constraints purge;
truncate table t_struc_node_resids_2;
drop table t_struc_node_resids_2 cascade constraints purge;
truncate table t_struc_type;
drop table t_struc_type cascade constraints purge;
truncate table t_struc_type_2;
drop table t_struc_type_2 cascade constraints purge;

truncate table t_data;
drop table t_data cascade constraints purge;
truncate table t_data_node;
drop table t_data_node cascade constraints purge;
truncate table t_data_struc_type;
drop table t_data_struc_type cascade constraints purge;

truncate table t_res;
drop table t_res cascade constraints purge;
truncate table t_res_uuid;
drop table t_res_uuid cascade constraints purge;
truncate table t_res_node;
drop table t_res_node cascade constraints purge;

truncate table t_rights;
drop table t_rights cascade constraints purge;
--
commit;