param([ValidateSet('Snapshot','Inspect','Compare')][string]$Mode='Inspect')
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
function Fingerprints {
 $result=[ordered]@{}
 $tables=Query 'select name from sys.tables where is_ms_shipped=0 order by name'
 foreach($entry in $tables.Rows){
  $name=$entry['name'];$rows=Query ("select * from dbo.["+ $name +"] order by 1")
  $canonical=ConvertTo-Json -InputObject @(Records $rows) -Depth 8 -Compress
  $bytes=[Text.Encoding]::UTF8.GetBytes($canonical)
  $sha=[Security.Cryptography.SHA256]::Create()
  try{$hash=([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-','').ToLowerInvariant()}finally{$sha.Dispose()}
  $result[$name]=[ordered]@{count=$rows.Rows.Count;sha256=$hash}
 }
 return $result
}
try{
 $connection.Open()
 if($Mode -eq 'Snapshot'){
  $fingerprints=Fingerprints
  $fingerprints|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Join-Path $out 'db-before.json') -Encoding UTF8
  @(Records (Query 'select store_product_id,quantity,price,status,updated_at from dbo.store_products order by store_product_id'))|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Join-Path $out 'stock-fixture-baseline.json') -Encoding UTF8
  Write-Output "Snapshot: $($fingerprints.Count) tables; only counts and SHA256 retained."
 } elseif($Mode -eq 'Compare'){
  $after=Fingerprints;$before=Get-Content -LiteralPath (Join-Path $out 'db-before.json') -Raw|ConvertFrom-Json
  $differences=@();foreach($name in $after.Keys){$prior=$before.$name;if($prior.sha256 -ne $after[$name].sha256){$differences+=,[ordered]@{table=$name;before=$prior;after=$after[$name]}}}
  [ordered]@{checkedAt=(Get-Date -Format o);tables=$after.Count;exactMatch=($differences.Count -eq 0);differences=$differences}|ConvertTo-Json -Depth 10|Set-Content -LiteralPath (Join-Path $out 'db-comparison.json') -Encoding UTF8
  Write-Output "Comparison: $($after.Count) tables; differences=$($differences.Count)"
 } else {
  $queries=[ordered]@{
   environment="select @@SERVERNAME server_name,DB_NAME() database_name,cast(SERVERPROPERTY('Edition') as nvarchar(100)) edition,cast(SERVERPROPERTY('ProductVersion') as nvarchar(100)) version"
   tables="select t.name table_name,(select count(*) from sys.columns c where c.object_id=t.object_id) columns from sys.tables t where t.is_ms_shipped=0 order by t.name"
   columns="select t.name table_name,c.column_id,c.name,ty.name sql_type,c.max_length,c.precision,c.scale,c.is_nullable,c.is_identity from sys.tables t join sys.columns c on c.object_id=t.object_id join sys.types ty on ty.user_type_id=c.user_type_id where t.is_ms_shipped=0 order by t.name,c.column_id"
   foreignKeys="select fk.name,object_name(fk.parent_object_id) table_name,c.name column_name,object_name(fk.referenced_object_id) referenced_table,rc.name referenced_column,fk.delete_referential_action_desc,fk.update_referential_action_desc,fk.is_disabled,fk.is_not_trusted from sys.foreign_keys fk join sys.foreign_key_columns fkc on fkc.constraint_object_id=fk.object_id join sys.columns c on c.object_id=fkc.parent_object_id and c.column_id=fkc.parent_column_id join sys.columns rc on rc.object_id=fkc.referenced_object_id and rc.column_id=fkc.referenced_column_id order by table_name,fk.name"
   checks="select object_name(parent_object_id) table_name,name,definition,is_disabled,is_not_trusted from sys.check_constraints order by table_name,name"
   indexes="select t.name table_name,i.name,i.type_desc,i.is_unique,i.is_primary_key,i.is_disabled,i.filter_definition,c.name column_name,ic.key_ordinal,ic.is_included_column from sys.tables t join sys.indexes i on i.object_id=t.object_id join sys.index_columns ic on ic.object_id=i.object_id and ic.index_id=i.index_id join sys.columns c on c.object_id=ic.object_id and c.column_id=ic.column_id where t.is_ms_shipped=0 order by t.name,i.index_id,ic.key_ordinal,ic.index_column_id"
   constraintViolations="select name from sys.check_constraints where is_disabled=1 or is_not_trusted=1 union all select name from sys.foreign_keys where is_disabled=1 or is_not_trusted=1"
   monetaryIntegrity="select o.order_id from dbo.orders o where o.total_amount <> coalesce((select sum(subtotal) from dbo.order_items i where i.order_id=o.order_id),0) union all select -c.checkout_id from dbo.checkout_sessions c where c.total_amount <> coalesce((select sum(total_amount) from dbo.orders o where o.checkout_id=c.checkout_id),0)"
   itemStoreIntegrity="select i.order_item_id from dbo.order_items i join dbo.orders o on o.order_id=i.order_id join dbo.store_products sp on sp.store_product_id=i.store_product_id where sp.store_id<>o.store_id or i.subtotal<>i.unit_price*i.quantity"
   negativeStock="select store_product_id from dbo.store_products where quantity<0 or price<0"
   duplicateMovements="select reference_order_id,store_product_id,type,count(*) duplicates from dbo.inventory_movements where type in ('ORDER','CANCEL_ORDER') group by reference_order_id,store_product_id,type having count(*)>1"
   brokenTimeline="with h as (select *,row_number() over(partition by order_id order by changed_at,history_id) rn,lag(new_status) over(partition by order_id order by changed_at,history_id) previous from dbo.order_status_history) select history_id from h where (rn=1 and old_status is not null) or (rn>1 and (old_status is null or old_status<>previous or old_status=new_status))"
   statusHistory="select o.order_id from dbo.orders o outer apply (select top 1 h.new_status from dbo.order_status_history h where h.order_id=o.order_id order by h.changed_at desc,h.history_id desc) latest where latest.new_status is null or latest.new_status<>o.order_status"
   stockLedger="with m as (select *,lag(quantity_after) over(partition by store_product_id order by created_at,movement_id) previous,row_number() over(partition by store_product_id order by created_at desc,movement_id desc) rn from dbo.inventory_movements) select m.movement_id from m join dbo.store_products sp on sp.store_product_id=m.store_product_id where cast(quantity_after as bigint)<>cast(quantity_before as bigint)+quantity_change or (previous is not null and quantity_before<>previous) or (rn=1 and quantity_after<>sp.quantity)"
   duplicatePrimaryImages="select product_id,count(*) primary_count from dbo.product_images where is_primary=1 group by product_id having count(*)>1"
   imageBinaryColumns="select table_name,column_name,data_type from information_schema.columns where table_name in ('product_images','brands') and data_type in ('binary','varbinary','image')"
   userRoles="select r.name role_name,u.status,count(*) count from dbo.users u join dbo.roles r on r.role_id=u.role_id group by r.name,u.status order by r.name,u.status"
   sessions="select checkout_id,status,total_amount from dbo.checkout_sessions order by checkout_id"
  }
  $result=[ordered]@{checkedAt=(Get-Date -Format o);mode='read-only';queries=[ordered]@{}}
  foreach($name in $queries.Keys){$result.queries[$name]=[ordered]@{sql=$queries[$name];rows=@(Records (Query $queries[$name]))}}
  $result|ConvertTo-Json -Depth 15|Set-Content -LiteralPath (Join-Path $out 'database-inspection.json') -Encoding UTF8
  Write-Output "Read-only inspection: $($queries.Count) queries; evidence/database-inspection.json"
 }
}finally{$connection.Dispose()}
