param([ValidateSet('Inspect','Cleanup')][string]$Mode='Inspect')
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$out=Join-Path $root 'docs/audit/full-system/evidence'
$config=@{}
Get-Content -LiteralPath (Join-Path $root '.env') -Encoding UTF8 | ForEach-Object {if($_ -match '^\s*([A-Z_]+)\s*=(.*)$'){$config[$matches[1]]=$matches[2].Trim()}}
function ConfigValue([string]$name,[string]$fallback) { $value=[Environment]::GetEnvironmentVariable($name); if($value){return $value}; if($config.ContainsKey($name)){return $config[$name]}; return $fallback }
$builder=New-Object System.Data.SqlClient.SqlConnectionStringBuilder
$builder['Data Source']=(ConfigValue 'DB_HOST' 'localhost')+','+(ConfigValue 'DB_PORT' '1433')
$builder['Initial Catalog']=ConfigValue 'DB_NAME' 'oneshop'
$builder['User ID']=ConfigValue 'DB_USERNAME' ''
$builder['Password']=ConfigValue 'DB_PASSWORD' ''
$builder['Encrypt']=$true
$builder['TrustServerCertificate']=(ConfigValue 'DB_TRUST_SERVER_CERTIFICATE' 'false') -eq 'true'
$builder['Connect Timeout']=8
$connection=New-Object System.Data.SqlClient.SqlConnection($builder.ConnectionString)
function Query([string]$sql) {
 $command=$connection.CreateCommand();$command.CommandTimeout=15;$command.CommandText=$sql
 $adapter=New-Object System.Data.SqlClient.SqlDataAdapter($command);$table=New-Object System.Data.DataTable
 [void]$adapter.Fill($table)
 return ,$table
}
function Records($table) {
 $result=@();foreach($row in $table.Rows){$record=[ordered]@{};foreach($column in $table.Columns){$value=$row[$column.ColumnName];$record[$column.ColumnName]=if($value -is [DBNull]){$null}else{$value}};$result+=,$record};return $result
}
try {
 $connection.Open()
 $environment=Query "select cast(SERVERPROPERTY('Edition') as nvarchar(100)) edition,DB_NAME() db"
 if($environment.Rows[0]['edition'] -notlike '*Developer*' -or $environment.Rows[0]['db'] -ne 'oneshop' -or (ConfigValue 'DB_HOST' '') -notin @('localhost','127.0.0.1')){throw 'Fixture helper requires the verified local Developer database'}
 $marker='AUD261009FSA'
 if($Mode -eq 'Inspect') {
  $queries=[ordered]@{
   stores="select * from dbo.stores where code like '$marker%' order by store_id"
   users="select user_id,email,full_name,role_id,status from dbo.users where email like 'audit-fsa-261009-%' order by user_id"
   unicode="select user_id,email from dbo.users where email='audit-unicode-261009@example.com'"
   products="select * from dbo.products where sku='$marker'"
   assignments="select a.* from dbo.staff_store_assignments a join dbo.users u on u.user_id=a.user_id where u.email like 'audit-fsa-261009-%'"
   stock="select sp.* from dbo.store_products sp join dbo.stores s on s.store_id=sp.store_id where s.code like '$marker%' order by sp.store_product_id"
   checkout="select c.* from dbo.checkout_sessions c join dbo.users u on u.user_id=c.user_id where u.email like 'audit-fsa-261009-%'"
   orders="select o.* from dbo.orders o join dbo.users u on u.user_id=o.user_id where u.email like 'audit-fsa-261009-%' order by order_id"
   items="select i.* from dbo.order_items i join dbo.orders o on o.order_id=i.order_id join dbo.users u on u.user_id=o.user_id where u.email like 'audit-fsa-261009-%'"
   payments="select p.* from dbo.payments p join dbo.orders o on o.order_id=p.order_id join dbo.users u on u.user_id=o.user_id where u.email like 'audit-fsa-261009-%'"
   history="select h.* from dbo.order_status_history h join dbo.orders o on o.order_id=h.order_id join dbo.users u on u.user_id=o.user_id where u.email like 'audit-fsa-261009-%' order by history_id"
   movements="select m.* from dbo.inventory_movements m join dbo.store_products sp on sp.store_product_id=m.store_product_id join dbo.stores s on s.store_id=sp.store_id where s.code like '$marker%' order by movement_id"
  }
  $results=[ordered]@{};foreach($name in $queries.Keys){$results[$name]=@(Records (Query $queries[$name]))}
  $results|ConvertTo-Json -Depth 10|Set-Content (Join-Path $out 'fixture-db.json') -Encoding UTF8
  $results.Keys|ForEach-Object {Write-Output "$_ $($results[$_].Count)"}
 } else {
  # Only disposable rows owned by the exact new fixture marker. Existing rows are never candidates.
  $command=$connection.CreateCommand();$command.CommandTimeout=20
  $command.CommandText=@'
set xact_abort on;
begin transaction;
declare @u table(id bigint primary key); insert @u select user_id from dbo.users where email in ('audit-fsa-261009-customer@example.com','audit-fsa-261009-staff@example.com');
declare @s table(id bigint primary key); insert @s select store_id from dbo.stores where code in ('AUD261009FSAA','AUD261009FSAB','AUD261009FSAC');
declare @p table(id bigint primary key); insert @p select product_id from dbo.products where sku='AUD261009FSA';
declare @sp table(id bigint primary key); insert @sp select store_product_id from dbo.store_products where store_id in (select id from @s) and product_id in (select id from @p);
declare @o table(id bigint primary key); insert @o select order_id from dbo.orders where user_id in (select id from @u) and store_id in (select id from @s);
if exists(select 1 from dbo.orders where store_id in (select id from @s) and order_id not in(select id from @o)) throw 50001,'Foreign order in fixture store: cleanup aborted',1;
if exists(select 1 from dbo.product_images where product_id in(select id from @p)) throw 50002,'Unexpected Cloudinary asset: cleanup aborted',1;
delete dbo.reviews where user_id in(select id from @u) and product_id in(select id from @p);
delete dbo.inventory_movements where store_product_id in(select id from @sp);
delete dbo.order_status_history where order_id in(select id from @o);
delete dbo.payments where order_id in(select id from @o);
delete dbo.order_items where order_id in(select id from @o);
delete dbo.orders where order_id in(select id from @o);
delete dbo.checkout_sessions where user_id in(select id from @u);
delete dbo.cart_items where cart_id in(select cart_id from dbo.carts where user_id in(select id from @u));
delete dbo.carts where user_id in(select id from @u);
delete dbo.customer_addresses where user_id in(select id from @u);
delete dbo.staff_store_assignments where user_id in(select id from @u) and store_id in(select id from @s);
delete dbo.store_products where store_product_id in(select id from @sp);
delete dbo.products where product_id in(select id from @p);
delete dbo.categories where name='AUD261009FSA Category';
delete dbo.brands where name='AUD261009FSA Brand';
delete dbo.stores where store_id in(select id from @s);
delete dbo.users where user_id in(select id from @u);
commit transaction;
'@
  [void]$command.ExecuteNonQuery();Write-Output 'Disposed only new audit marker fixtures; originals must match SHA256 baseline.'
 }
} finally {$connection.Close();$connection.Dispose()}
