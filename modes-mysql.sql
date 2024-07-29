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
-- Table structure for table `callsign_list`
--

DROP TABLE IF EXISTS `callsign_list`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `callsign_list` (
  `callsign_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `icao_number` char(6) NOT NULL,
  `callsign` char(8) NOT NULL COMMENT 'Transmitted Callsign',
  PRIMARY KEY (`callsign_id`),
  UNIQUE KEY `Index_Callsign` (`icao_number`,`callsign_id`),
  KEY `FK_callsign_icao` (`icao_number`),
  CONSTRAINT `FK_callsign_icao` FOREIGN KEY (`icao_number`) REFERENCES `icao_list` (`icao_number`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Callsign';
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Mode-S ICAO';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `position_echo`
--

DROP TABLE IF EXISTS `position_echo`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `position_echo` (
  `position_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Position ID',
  `radar_site` int unsigned NOT NULL DEFAULT '0' COMMENT 'Radar Site that generated this position report',
  `icao_number` char(6) NOT NULL COMMENT 'ICAO Number',
  `utcdetect` bigint unsigned NOT NULL COMMENT 'UTC microseconds',
  `amplitude` int DEFAULT NULL COMMENT 'Receiver Amplitude',
  `radar_iid` int DEFAULT NULL,
  `radar_si` tinyint(1) NOT NULL DEFAULT '0',
  `latitude` float NOT NULL COMMENT 'latitude in degrees',
  `longitude` float NOT NULL COMMENT 'longitude in degrees',
  `altitude` int DEFAULT NULL COMMENT 'Reported Altitude in Feet',
  `verticalTrend` int NOT NULL DEFAULT '0' COMMENT 'Calculated Vertical Trend -1 = down, 0 = level, 1 = up',
  `onground` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'On Ground Bit default false',
  PRIMARY KEY (`position_id`),
  KEY `FK_echo_icao` (`icao_number`),
  CONSTRAINT `FK_echo_icao` FOREIGN KEY (`icao_number`) REFERENCES `icao_list` (`icao_number`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Target Echoes';
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
  `multiple_threats` tinyint(1) NOT NULL DEFAULT '0',
  `threat_terminated` tinyint(1) NOT NULL DEFAULT '0',
  `identity_data_raw` varchar(45) DEFAULT NULL,
  `type_data_raw` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`tcas_id`) USING BTREE,
  KEY `FK_tcas_icao` (`icao_number`),
  CONSTRAINT `FK_tcas_icao` FOREIGN KEY (`icao_number`) REFERENCES `icao_list` (`icao_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='TCAS Alerts';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tracks`
--

DROP TABLE IF EXISTS `tracks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tracks` (
  `track_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'Track ID',
  `radar_site` int unsigned NOT NULL DEFAULT '0' COMMENT 'Radar Site that generated this target report',
  `icao_number` char(6) NOT NULL COMMENT 'ICAO Number',
  `utcdetect` bigint unsigned NOT NULL COMMENT 'UTC microseconds track first detected',
  `utcupdate` bigint unsigned NOT NULL COMMENT 'UTC microseconds track last updated',
  `altitude` int DEFAULT NULL COMMENT 'Altitude in feet',
  `altitudedf00` int DEFAULT NULL,
  `altitudedf04` int DEFAULT NULL,
  `altitudedf16` int DEFAULT NULL,
  `altitudedf17` int DEFAULT NULL,
  `altitudedf18` int DEFAULT NULL,
  `altitudedf20` int DEFAULT NULL,
  `amplitude` int DEFAULT NULL COMMENT 'Receiver Amplitude',
  `radar_iid` int DEFAULT NULL,
  `radar_si` tinyint(1) NOT NULL DEFAULT '0',
  `groundSpeed` float DEFAULT NULL COMMENT 'Speed over the ground',
  `groundTrack` float DEFAULT NULL COMMENT 'Heading in relation to True North',
  `gsComputed` float DEFAULT NULL COMMENT 'Computed Speed over the ground',
  `gtComputed` float DEFAULT NULL COMMENT 'Computed Heading in relation to True North',
  `callsign` char(8) DEFAULT NULL COMMENT 'Transmitted Callsign',
  `latitude` float DEFAULT NULL,
  `longitude` float DEFAULT NULL,
  `verticalRate` int DEFAULT NULL,
  `verticalTrend` int NOT NULL DEFAULT '0',
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
  `active` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Active or Inactive Track',
  PRIMARY KEY (`track_id`) USING BTREE,
  UNIQUE KEY `TrackIDIndex` (`icao_number`,`radar_site`) USING BTREE,
  KEY `FK_icao` (`icao_number`),
  CONSTRAINT `FK_icao` FOREIGN KEY (`icao_number`) REFERENCES `icao_list` (`icao_number`)
) ENGINE=InnoDB AUTO_INCREMENT=0 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Track';
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

-- Dump completed on 2024-07-29 14:11:18
