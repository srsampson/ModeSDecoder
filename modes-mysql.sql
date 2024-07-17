CREATE DATABASE  IF NOT EXISTS `modes` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `modes`;
-- MySQL dump 10.13  Distrib 8.0.38, for Win64 (x86_64)
--
-- Host: localhost    Database: modes
-- ------------------------------------------------------
-- Server version	9.0.0

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `callsign`
--

DROP TABLE IF EXISTS `callsign`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `callsign` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `acid` char(6) NOT NULL,
  `utcdetect` bigint NOT NULL COMMENT 'UTC Time detected',
  `utcupdate` bigint NOT NULL COMMENT 'UTC Time updated',
  `callsign` char(8) NOT NULL COMMENT 'Transmitted Callsign',
  `flight_id` bigint unsigned NOT NULL,
  `radar_id` int unsigned NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `Index_Callsign` (`acid`,`callsign`,`flight_id`,`radar_id`),
  KEY `FK_callsign_acid` (`acid`),
  CONSTRAINT `FK_callsign_acid` FOREIGN KEY (`acid`) REFERENCES `modestable` (`acid`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Callsigns Associated with Targets';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `modestable`
--

DROP TABLE IF EXISTS `modestable`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `modestable` (
  `acid` char(6) NOT NULL,
  `utcdetect` bigint NOT NULL COMMENT 'UTC Time detected',
  `utcupdate` bigint NOT NULL COMMENT 'UTC Time updated',
  `acft_reg` varchar(45) DEFAULT NULL,
  `acft_model` varchar(45) DEFAULT NULL,
  `acft_operator` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`acid`) USING BTREE,
  KEY `Index_reg` (`acft_reg`),
  KEY `Index_model` (`acft_model`),
  KEY `Index_operator` (`acft_operator`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Mode-S ICAO Received';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `target`
--

DROP TABLE IF EXISTS `target`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `target` (
  `flight_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Flight ID',
  `radar_id` int unsigned NOT NULL DEFAULT '0' COMMENT 'Radar ID that generated this target report',
  `acid` char(6) NOT NULL COMMENT 'Aircraft ID',
  `utcdetect` bigint NOT NULL COMMENT 'UTC Time track detected',
  `utcupdate` bigint NOT NULL COMMENT 'UTC Time track updated',
  `altitude` int DEFAULT NULL COMMENT 'Altitude in feet',
  `altitudedf00` int DEFAULT NULL,
  `altitudedf04` int DEFAULT NULL,
  `altitudedf16` int DEFAULT NULL,
  `altitudedf17` int DEFAULT NULL,
  `altitudedf18` int DEFAULT NULL,
  `altitudedf20` int DEFAULT NULL,
  `radariid` int DEFAULT NULL,
  `si` tinyint(1) NOT NULL DEFAULT '0',
  `groundSpeed` double DEFAULT NULL COMMENT 'Speed over the ground',
  `groundTrack` double DEFAULT NULL COMMENT 'Heading in relation to True North',
  `gsComputed` double DEFAULT NULL COMMENT 'Computed Speed over the ground',
  `gtComputed` double DEFAULT NULL COMMENT 'Computed Heading in relation to True North',
  `callsign` char(8) DEFAULT NULL COMMENT 'Transmitted Callsign',
  `latitude` double DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `verticalRate` int DEFAULT NULL,
  `verticalTrend` tinyint(1) NOT NULL DEFAULT '0',
  `quality` int DEFAULT NULL,
  `squawk` char(4) DEFAULT NULL,
  `alert` tinyint(1) NOT NULL DEFAULT '0',
  `emergency` tinyint(1) NOT NULL DEFAULT '0',
  `spi` tinyint(1) NOT NULL DEFAULT '0',
  `onground` tinyint(1) NOT NULL DEFAULT '0',
  `hijack` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Squawk Code 7500 detected',
  `comm_out` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Squawk Code 7600 detected',
  `hadAlert` tinyint(1) NOT NULL DEFAULT '0',
  `hadEmergency` tinyint(1) NOT NULL DEFAULT '0',
  `hadSPI` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`flight_id`) USING BTREE,
  UNIQUE KEY `FltIDIndex` (`flight_id`,`acid`,`radar_id`) USING BTREE,
  KEY `FK_acid` (`acid`),
  CONSTRAINT `FK_acid` FOREIGN KEY (`acid`) REFERENCES `modestable` (`acid`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Active Target Tracks';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb3_general_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'NO_AUTO_VALUE_ON_ZERO' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`root`@`localhost`*/ /*!50003 TRIGGER `insertmodes` BEFORE INSERT ON `target` FOR EACH ROW BEGIN
  SET @tcount = (SELECT count(1) FROM modestable WHERE acid=NEW.acid);
  IF @tcount > 0 THEN
    UPDATE modestable SET utcupdate=NEW.utcdetect WHERE acid=NEW.acid;
  ELSE
    INSERT INTO modestable (acid,utcdetect,utcupdate) VALUES (NEW.acid, NEW.utcdetect, NEW.utcdetect);
  END IF;
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb3_general_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'NO_AUTO_VALUE_ON_ZERO' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`root`@`localhost`*/ /*!50003 TRIGGER `updatemodes` BEFORE UPDATE ON `target` FOR EACH ROW BEGIN
  UPDATE modestable SET utcupdate=NEW.utcupdate WHERE acid=NEW.acid;
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;

--
-- Table structure for table `targetecho`
--

DROP TABLE IF EXISTS `targetecho`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `targetecho` (
  `record_num` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Flight ID',
  `flight_id` bigint unsigned NOT NULL,
  `radar_id` int unsigned NOT NULL DEFAULT '0' COMMENT 'Radar ID that generated this target report',
  `acid` char(6) NOT NULL COMMENT 'Aircraft ID',
  `utcdetect` bigint NOT NULL COMMENT 'UTC Time detected',
  `radariid` int DEFAULT NULL,
  `si` tinyint(1) NOT NULL DEFAULT '0',
  `latitude` double NOT NULL COMMENT 'latitude in degrees',
  `longitude` double NOT NULL COMMENT 'longitude in degrees',
  `altitude` int DEFAULT NULL COMMENT 'Reported Altitude in Feet',
  `verticalTrend` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Calculated Vertical Trend -1 = down, 0 = level, 1 = up',
  `onground` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`record_num`),
  KEY `FK_targetecho_acid` (`acid`),
  CONSTRAINT `FK_targetecho_acid` FOREIGN KEY (`acid`) REFERENCES `modestable` (`acid`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='History of Target Positions';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `targethistory`
--

DROP TABLE IF EXISTS `targethistory`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `targethistory` (
  `record_num` bigint unsigned NOT NULL AUTO_INCREMENT,
  `flight_id` bigint unsigned NOT NULL DEFAULT '0',
  `radar_id` int unsigned NOT NULL DEFAULT '0',
  `acid` char(6) NOT NULL,
  `utcdetect` timestamp NULL DEFAULT NULL COMMENT 'UTC Time detected',
  `utcfadeout` timestamp NULL DEFAULT NULL COMMENT 'UTC Fadeout Time',
  `radariid` int DEFAULT NULL,
  `si` tinyint(1) NOT NULL DEFAULT '0',
  `altitude` int DEFAULT NULL,
  `altitudedf00` int DEFAULT NULL,
  `altitudedf04` int DEFAULT NULL,
  `altitudedf16` int DEFAULT NULL,
  `altitudedf17` int DEFAULT NULL,
  `altitudedf18` int DEFAULT NULL,
  `altitudedf20` int DEFAULT NULL,
  `groundSpeed` double DEFAULT NULL,
  `groundTrack` double DEFAULT NULL,
  `gsComputed` double DEFAULT NULL,
  `gtComputed` double DEFAULT NULL,
  `callsign` char(8) DEFAULT NULL,
  `latitude` double DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `verticalRate` int DEFAULT NULL,
  `verticalTrend` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Calculated Vertical Trend -1 = down, 0 = level, 1 = up',
  `squawk` char(4) DEFAULT NULL,
  `alert` tinyint(1) NOT NULL DEFAULT '0',
  `emergency` tinyint(1) NOT NULL DEFAULT '0',
  `spi` tinyint(1) NOT NULL DEFAULT '0',
  `onground` tinyint(1) NOT NULL DEFAULT '0',
  `hijack` tinyint(1) NOT NULL DEFAULT '0',
  `comm_out` tinyint(1) NOT NULL DEFAULT '0',
  `hadAlert` tinyint(1) NOT NULL DEFAULT '0',
  `hadEmergency` tinyint(1) NOT NULL DEFAULT '0',
  `hadSPI` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`record_num`) USING BTREE,
  UNIQUE KEY `FltIDIndex` (`flight_id`,`acid`,`radar_id`) USING BTREE,
  KEY `FK_targethistory_acid` (`acid`),
  CONSTRAINT `FK_targethistory_acid` FOREIGN KEY (`acid`) REFERENCES `modestable` (`acid`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Targets No Longer Active';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping events for database 'modes'
--

--
-- Dumping routines for database 'modes'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2024-07-17 18:10:28
