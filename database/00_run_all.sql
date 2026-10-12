/* =============================================================================
   OneShop - chay toan bo theo thu tu (SQLCMD mode): tao database, bang, index, du lieu mau va kiem tra.
   Chay tu THU MUC database/:
     sqlcmd -S localhost -E -C -f 65001 -b -I -i 00_run_all.sql
   (SQL login: thay -E bang -U <user> -P <password>)
   Chi dung cho moi truong phat trien/thu nghiem: chay lai se XOA du lieu hien co cua database oneshop.
   ========================================================================== */
:on error exit
:r 01_create_database.sql
:r 02_create_tables.sql
:r 03_create_indexes.sql
:r 04_seed_data.sql
:r 05_verify.sql
