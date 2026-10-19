-- Deliberately clears the complete Redis database used by ShopFlash.
-- Invoke only through Invoke-BackupRecoveryDemo.ps1 with explicit confirmation.

redis.call('FLUSHDB', 'ASYNC')
return { 'DATA_LOSS_REQUESTED|REDIS' }
