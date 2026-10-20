-- ONLY for the helper-owned restored test DB after collision checks. Never production.
START TRANSACTION;
INSERT INTO __hr_typepos(posTypeId,posTypeName,fio,mobile,whatsapp,access,parol,is_active) VALUES
(2000000001,'FFL E2E Driver','FFL E2E Driver','+70000000000',NULL,1,'ffl-e2e-only-42',1),
(2000000002,'FFL E2E Disabled','FFL E2E Disabled',NULL,NULL,0,'ffl-e2e-only-42',0),
(2000000010,'FFL History 0','FFL History 0',NULL,NULL,0,'ffl-e2e-only-42',0),
(2000000011,'FFL History 1','FFL History 1',NULL,NULL,0,'ffl-e2e-only-42',0),
(2000000012,'FFL History 2','FFL History 2',NULL,NULL,0,'ffl-e2e-only-42',0),
(2000000013,'FFL History 3','FFL History 3',NULL,NULL,0,'ffl-e2e-only-42',0),
(2000000014,'FFL History 4','FFL History 4',NULL,NULL,0,'ffl-e2e-only-42',0);
INSERT INTO reys(id,nomer_z,id_v,name_c,col,p,vremya,date,status,col_s,sort_r,st_vv,date_att_log) VALUES
(2000000001,'FFL-E2E-FIRST',2000000001,'FFL E2E client',3,0,'с 11:30 FFL E2E comment','2099-12-31',2,1,1,0,NULL),
(2000000002,'FFL-E2E-DELIVERED',2000000001,'FFL E2E delivered',1,0,'','2099-12-31',2,2,2,1,'2026-05-18 12:00:00'),
(2000000003,'FFL-E2E-FOREIGN',2000000002,'FFL E2E foreign',1,0,'','2099-12-31',2,2,3,0,NULL),
(2000000010,'FFL-E2E-HISTORY0',2000000010,'FFL E2E client',1,0,'','2099-12-25',2,2,1,1,'2026-05-18 12:00:00'),
(2000000011,'FFL-E2E-HISTORY1',2000000011,'FFL E2E client',1,0,'','2099-12-26',2,2,1,1,'2026-05-18 12:00:00'),
(2000000012,'FFL-E2E-HISTORY2',2000000012,'FFL E2E client',1,0,'','2099-12-27',2,2,1,1,'2026-05-18 12:00:00'),
(2000000013,'FFL-E2E-HISTORY3',2000000013,'FFL E2E client',1,0,'','2099-12-28',2,2,1,1,'2026-05-18 12:00:00'),
(2000000014,'FFL-E2E-HISTORY4',2000000014,'FFL E2E client',1,0,'','2099-12-29',2,2,1,1,'2026-05-18 12:00:00');
INSERT INTO reys_den(id,nomer_z,id_v,name_c,col,p,vremya,date,status,col_s,sort_r,st_vv,date_att_log) VALUES
(2000000001,'FFL-E2E-SECOND',2000000001,'FFL E2E second',2,0,'FFL E2E second comment','2099-12-31',2,2,1,0,NULL),
(2000000004,'FFL-E2E-NOTIFICATION',2000000001,'FFL E2E new order',1,0,'','2099-12-31',2,2,4,1,'2026-05-18 12:00:00');
INSERT INTO meneger_ffl(id,meneger,tel,short_tel,status) VALUES(2000000001,'FFL E2E manager','+70000000000','0000',1);
INSERT INTO meneger_contr_ffl(id,nomer_z,ooo,adress,tell_klient,tell_gruzchik,id_m) VALUES
(2000000001,'FFL-E2E-FIRST','FFL E2E client','FFL controlled address','+70000000000',NULL,2000000001),
(2000000002,'FFL-E2E-SECOND','FFL E2E second','FFL controlled A','+70000000000',NULL,2000000001),
(2000000003,' FFL-E2E-SECOND','FFL E2E second','FFL controlled B','+70000000001',NULL,2000000001);
INSERT INTO phone(id,name,nomer,nomer_full,access) VALUES(2000000001,'FFL E2E Dispatcher','0000','+70000000000',1);
COMMIT;
