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
-- Table structure for table `alert_list`
--

DROP TABLE IF EXISTS `alert_list`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `alert_list` (
  `alert_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Alert ID',
  `icao_number` char(6) NOT NULL,
  `utcdetect` bigint unsigned NOT NULL,
  `spi` tinyint(1) NOT NULL DEFAULT '0',
  `alert` tinyint(1) NOT NULL DEFAULT '0',
  `emergency` tinyint(1) NOT NULL DEFAULT '0',
  `hijack` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Squawk Code 7500 detected',
  `comm_out` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Squawk Code 7600 detected',
  PRIMARY KEY (`alert_id`),
  KEY `FK_alert_icao` (`icao_number`) /*!80000 INVISIBLE */,
  CONSTRAINT `FK_alert_icao` FOREIGN KEY (`icao_number`) REFERENCES `icao_list` (`icao_number`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Alert Table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `altitude_list`
--

DROP TABLE IF EXISTS `altitude_list`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `altitude_list` (
  `altitude_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Altitude ID',
  `radar_site` int unsigned DEFAULT NULL,
  `icao_number` char(6) NOT NULL COMMENT 'ICAO Number',
  `utcdetect` bigint unsigned NOT NULL COMMENT 'UTC microseconds',
  `altitude` int DEFAULT NULL COMMENT 'Altitude in feet',
  `altitude_df00` int DEFAULT NULL,
  `altitude_df04` int DEFAULT NULL,
  `altitude_df16` int DEFAULT NULL,
  `altitude_df17` int DEFAULT NULL,
  `altitude_df18` int DEFAULT NULL,
  `altitude_df20` int DEFAULT NULL,
  `verticalRate` int DEFAULT NULL,
  `verticalTrend` int NOT NULL DEFAULT '0' COMMENT 'Calculated Vertical Trend -1 = down, 0 = level, 1 = up',
  `onground` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'On Ground',
  PRIMARY KEY (`altitude_id`),
  KEY `FK_altitude_icao` (`icao_number`),
  CONSTRAINT `FK_altitude_icao` FOREIGN KEY (`icao_number`) REFERENCES `icao_list` (`icao_number`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Altitude List Table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `amplitude_list`
--

DROP TABLE IF EXISTS `amplitude_list`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `amplitude_list` (
  `amplitude_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Amplitude ID',
  `radar_site` int unsigned DEFAULT NULL,
  `icao_number` char(6) NOT NULL COMMENT 'ICAO Number',
  `utcdetect` bigint unsigned NOT NULL COMMENT 'UTC microseconds',
  `amplitude` int DEFAULT NULL,
  PRIMARY KEY (`amplitude_id`),
  KEY `FK_amplitude_icao` (`icao_number`),
  CONSTRAINT `FK_amplitude_icao` FOREIGN KEY (`icao_number`) REFERENCES `icao_list` (`icao_number`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Amplitude List Table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `callsign_list`
--

DROP TABLE IF EXISTS `callsign_list`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `callsign_list` (
  `callsign_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Callsign ID',
  `callsign` char(8) NOT NULL COMMENT 'Transmitted Callsign',
  `icao_number` char(6) NOT NULL,
  `utcdetect` bigint unsigned NOT NULL,
  PRIMARY KEY (`callsign_id`),
  KEY `index_callsign` (`callsign`) USING BTREE,
  KEY `FK_callsign_icao` (`icao_number`) /*!80000 INVISIBLE */,
  CONSTRAINT `FK_callsign_icao` FOREIGN KEY (`icao_number`) REFERENCES `icao_list` (`icao_number`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Callsign Table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `icao_list`
--

DROP TABLE IF EXISTS `icao_list`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `icao_list` (
  `icao_number` char(6) NOT NULL COMMENT 'ICAO Number Received by Radar Site',
  `country` varchar(45) DEFAULT NULL COMMENT 'Country must be manually Entered',
  `registration` varchar(45) DEFAULT NULL COMMENT 'Currently USA Only',
  PRIMARY KEY (`icao_number`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Mode-S ICAO Table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `position_echo`
--

DROP TABLE IF EXISTS `position_echo`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `position_echo` (
  `position_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Position ID',
  `radar_site` int unsigned DEFAULT NULL,
  `icao_number` char(6) NOT NULL COMMENT 'ICAO Number',
  `utcdetect` bigint unsigned NOT NULL COMMENT 'UTC microseconds',
  `latitude` float NOT NULL COMMENT 'latitude in degrees',
  `longitude` float NOT NULL COMMENT 'longitude in degrees',
  `verticalTrend` int NOT NULL DEFAULT '0' COMMENT 'color of position',
  `onground` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'On Ground, trend ignored',
  PRIMARY KEY (`position_id`),
  KEY `FK_echo_icao` (`icao_number`),
  CONSTRAINT `FK_echo_icao` FOREIGN KEY (`icao_number`) REFERENCES `icao_list` (`icao_number`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Position Echo Table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `radar_list`
--

DROP TABLE IF EXISTS `radar_list`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `radar_list` (
  `radar_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Radar ID',
  `radar_site` int unsigned DEFAULT NULL,
  `icao_number` char(6) NOT NULL COMMENT 'ICAO Number',
  `utcdetect` bigint unsigned NOT NULL COMMENT 'UTC microseconds',
  `radar_iid` int DEFAULT NULL,
  `radar_si` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`radar_id`),
  KEY `FK_radar_icao` (`icao_number`),
  CONSTRAINT `FK_radar_icao` FOREIGN KEY (`icao_number`) REFERENCES `icao_list` (`icao_number`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Radar List Table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `speed_list`
--

DROP TABLE IF EXISTS `speed_list`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `speed_list` (
  `speed_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Speed ID',
  `radar_site` int unsigned DEFAULT NULL,
  `icao_number` char(6) NOT NULL COMMENT 'ICAO Number',
  `utcdetect` bigint unsigned NOT NULL COMMENT 'UTC microseconds',
  `groundSpeed` float DEFAULT NULL COMMENT 'Speed over the ground',
  `groundTrack` float DEFAULT NULL COMMENT 'Heading in relation to True North',
  `gsComputed` float DEFAULT NULL COMMENT 'Computed Speed over the ground',
  `gtComputed` float DEFAULT NULL COMMENT 'Computed Heading in relation to True North',
  PRIMARY KEY (`speed_id`),
  KEY `FK_speed_icao` (`icao_number`),
  CONSTRAINT `FK_speed_icao` FOREIGN KEY (`icao_number`) REFERENCES `icao_list` (`icao_number`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Speed List Table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `squawk_list`
--

DROP TABLE IF EXISTS `squawk_list`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `squawk_list` (
  `squawk_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Squawk ID',
  `squawk` char(4) DEFAULT NULL,
  `icao_number` char(6) NOT NULL,
  `utcdetect` bigint unsigned NOT NULL,
  PRIMARY KEY (`squawk_id`),
  KEY `index_squawk` (`squawk`) USING BTREE,
  KEY `FK_squawk_icao` (`icao_number`) /*!80000 INVISIBLE */,
  CONSTRAINT `FK_squawk_icao` FOREIGN KEY (`icao_number`) REFERENCES `icao_list` (`icao_number`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Squawk Table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tcas_alerts`
--

DROP TABLE IF EXISTS `tcas_alerts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tcas_alerts` (
  `tcas_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'TCAS ID',
  `utcdetect` bigint unsigned NOT NULL COMMENT 'UTC microseconds',
  `icao_number` char(6) NOT NULL COMMENT 'ICAO Number',
  `df_source` int unsigned DEFAULT NULL,
  `tti_bits` int unsigned DEFAULT NULL,
  `threat_icao` char(6) NOT NULL COMMENT 'Threat ICAO ID',
  `threat_relative_altitude` int DEFAULT NULL,
  `threat_altitude` int DEFAULT NULL,
  `threat_bearing` float DEFAULT NULL,
  `threat_range` float DEFAULT NULL,
  `ara_bits` int unsigned DEFAULT NULL,
  `rac_bits` int unsigned DEFAULT NULL,
  `active_ra` tinyint(1) NOT NULL DEFAULT '0',
  `single_ra` tinyint(1) NOT NULL DEFAULT '0',
  `multiple_ra` tinyint(1) NOT NULL DEFAULT '0',
  `threat_terminated` tinyint(1) NOT NULL DEFAULT '0',
  `identity_data_raw` varchar(45) DEFAULT NULL,
  `type_data_raw` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`tcas_id`) USING BTREE,
  KEY `FK_tcas_icao` (`icao_number`),
  CONSTRAINT `FK_tcas_icao` FOREIGN KEY (`icao_number`) REFERENCES `icao_list` (`icao_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='TCAS Alert Table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tracks`
--

DROP TABLE IF EXISTS `tracks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tracks` (
  `track_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Track ID',
  `icao_number` char(6) NOT NULL COMMENT 'ICAO Number',
  `radar_site` int unsigned DEFAULT NULL COMMENT 'Radar Site Number',
  `utcdetect` bigint unsigned NOT NULL COMMENT 'UTC microseconds track first detected',
  `utcupdate` bigint unsigned NOT NULL COMMENT 'UTC microseconds track last updated',
  `active` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Active or Inactive Track',
  `quality` int DEFAULT NULL,
  PRIMARY KEY (`track_id`) USING BTREE,
  KEY `FK_tracks_icao` (`icao_number`) /*!80000 INVISIBLE */,
  CONSTRAINT `FK_tracks_icao` FOREIGN KEY (`icao_number`) REFERENCES `icao_list` (`icao_number`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Tracks Table';
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
/*!50003 CREATE*/ /*!50017 DEFINER=`root`@`localhost`*/ /*!50003 TRIGGER `insert_icao` BEFORE INSERT ON `tracks` FOR EACH ROW BEGIN
  SET @tcount = (SELECT count(*) FROM icao_list WHERE icao_number=NEW.icao_number);
  IF @tcount = 0 THEN
    INSERT INTO icao_list (icao_number) VALUES (NEW.icao_number);
  END IF;
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;

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

-- Dump completed on 2024-08-04 11:29:52
