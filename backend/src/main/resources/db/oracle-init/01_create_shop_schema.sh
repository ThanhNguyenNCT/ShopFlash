#!/bin/bash
# Tạo tablespace SHOP_DATA, user SHOP và quyền trong PDB, trước khi backend chạy Flyway bằng user SHOP.
#
# compose.yaml mount thư mục này vào /opt/oracle/scripts/startup. Image Oracle source (không exec) file này
# mỗi lần container khởi động, nên: mọi bước đều "đã có thì bỏ qua", và không được gọi exit.
# Mật khẩu SHOP lấy từ SHOP_PASSWORD (compose truyền từ ORACLE_APP_PASSWORD) và được đồng bộ lại mỗi lần chạy.
#
# Chạy tay: docker compose exec -T oracle bash /opt/oracle/scripts/startup/01_create_shop_schema.sh
#
# DDL không nhận bind variable nên ở đây phải ghép chuỗi trong execute immediate. Chỉ an toàn vì mật khẩu đã
# được kiểm tra bằng regex ở dưới và tên đường dẫn đọc từ dba_data_files. Đừng chép cách này cho SQL nghiệp vụ.

shopflash_create_shop_schema() {
  local pdb="${ORACLE_PDB:-FREEPDB1}"

  if [[ ! "${SHOP_PASSWORD:-}" =~ ^[A-Za-z][A-Za-z0-9_]*$ ]]; then
    echo "shopflash: SHOP_PASSWORD phải bắt đầu bằng chữ cái và chỉ gồm chữ, số, '_'. Bỏ qua tạo user SHOP." >&2
    return 1
  fi

  "$ORACLE_HOME/bin/sqlplus" -s / as sysdba <<SQL
whenever sqlerror exit failure
set define off feedback off
alter session set container = ${pdb};

declare
  l_count number;
  l_dir   varchar2(500);
begin
  select count(*) into l_count from dba_tablespaces where tablespace_name = 'SHOP_DATA';
  if l_count = 0 then
    -- Đặt datafile cạnh datafile SYSTEM của PDB, không phụ thuộc đường dẫn của từng image.
    select substr(file_name, 1, instr(file_name, '/', -1)) into l_dir
    from   dba_data_files
    where  tablespace_name = 'SYSTEM';
    execute immediate 'create tablespace shop_data datafile ''' || l_dir || 'shop_data01.dbf'''
      || ' size 100m autoextend on next 50m maxsize unlimited';
  end if;

  select count(*) into l_count from dba_users where username = 'SHOP';
  if l_count = 0 then
    -- Mật khẩu tạm, ghi đè ngay bằng ALTER USER bên dưới; mật khẩu thật không nằm trong text của khối PL/SQL.
    execute immediate 'create user shop identified by "Init_' || dbms_random.string('X', 24) || '"'
      || ' default tablespace shop_data temporary tablespace temp quota unlimited on shop_data';
  end if;
end;
/

-- Câu lệnh cấp cao nhất: Oracle che mật khẩu trong V\$SQL. Chạy mỗi lần để đồng bộ với .env và mở khóa tài khoản.
alter user shop identified by "${SHOP_PASSWORD}" account unlock;

grant create session, create table, create view, create sequence, create procedure,
      create trigger, create materialized view to shop;
-- Query processing: DBMS_XPLAN.DISPLAY_CURSOR cần đọc V\$SQL, V\$SQL_PLAN.
grant select_catalog_role to shop;
-- Backup & recovery: Data Pump vào DATA_PUMP_DIR.
grant read, write on directory data_pump_dir to shop;
exit
SQL
  local status=$?
  if [ $status -eq 0 ]; then
    echo "shopflash: user SHOP ready in ${pdb}"
  else
    echo "shopflash: tạo user SHOP thất bại (sqlplus exit ${status})" >&2
  fi
  return $status
}

shopflash_create_shop_schema
