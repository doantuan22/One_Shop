/* =============================================================================
   OneShop - Phase 2 - chay toan bo theo thu tu (SQLCMD mode)
   Chay tu THU MUC database/:
     sqlcmd -S localhost -E -C -f 65001 -b -i 00_run_all.sql
   (SQL login: thay -E bang -U <user> -P <password>)
   ========================================================================== */
:on error exit
:r 01_create_database.sql
:r 02_create_tables.sql
:r 03_create_indexes.sql
:r 04_seed_data.sql
:r 05_verify.sql
