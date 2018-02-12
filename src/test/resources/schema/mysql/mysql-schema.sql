-- MySQL dump 10.13  Distrib 5.7.20, for Linux (x86_64)
--
-- Host: localhost    Database: game
-- ------------------------------------------------------
-- Server version	5.7.20

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `EVT_JOURNAL`
--

DROP TABLE IF EXISTS `EVT_JOURNAL`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `EVT_JOURNAL` (
  `ordering` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `persistence_id` varchar(255) NOT NULL,
  `sequence_number` bigint(20) NOT NULL,
  `deleted` tinyint(1) DEFAULT '0',
  `tags` varchar(255) DEFAULT NULL,
  `message` blob NOT NULL,
  PRIMARY KEY (`persistence_id`,`sequence_number`),
  UNIQUE KEY `ordering` (`ordering`),
  UNIQUE KEY `journal_ordering_idx` (`ordering`)
) ENGINE=InnoDB AUTO_INCREMENT=143 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `EVT_SNAPSHOT`
--

DROP TABLE IF EXISTS `EVT_SNAPSHOT`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `EVT_SNAPSHOT` (
  `persistence_id` varchar(255) NOT NULL,
  `sequence_number` bigint(20) NOT NULL,
  `created` bigint(20) NOT NULL,
  `snapshot` blob NOT NULL,
  PRIMARY KEY (`persistence_id`,`sequence_number`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `REF_GAME`
--

DROP TABLE IF EXISTS `REF_GAME`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `REF_GAME` (
  `id` varchar(36) NOT NULL,
  `type` varchar(10) NOT NULL,
  `status` varchar(10) NOT NULL,
  `code` varchar(36) NOT NULL,
  `parents` varchar(255) DEFAULT NULL,
  `country_code` varchar(2) NOT NULL,
  `title` varchar(255) DEFAULT NULL,
  `start_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `timezone` varchar(10) NOT NULL,
  `end_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `input_type` varchar(20) NOT NULL,
  `input_point` int(11) DEFAULT NULL,
  `tags` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `REF_GAME_EAN`
--

DROP TABLE IF EXISTS `REF_GAME_EAN`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `REF_GAME_EAN` (
  `game_id` varchar(36) NOT NULL,
  `ean` varchar(30) NOT NULL,
  PRIMARY KEY (`game_id`,`ean`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `REF_GAME_FREECODE`
--

DROP TABLE IF EXISTS `REF_GAME_FREECODE`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `REF_GAME_FREECODE` (
  `game_id` varchar(36) NOT NULL,
  `freecode` varchar(30) NOT NULL,
  PRIMARY KEY (`game_id`,`freecode`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `REF_GAME_LIMIT`
--

DROP TABLE IF EXISTS `REF_GAME_LIMIT`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `REF_GAME_LIMIT` (
  `game_id` varchar(36) NOT NULL,
  `type` varchar(20) NOT NULL,
  `unit` varchar(20) NOT NULL,
  `unit_value` int(11) DEFAULT NULL,
  `value` int(11) NOT NULL,
  KEY `idx_game_id` (`game_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `REF_GAME_PRIZE`
--

DROP TABLE IF EXISTS `REF_GAME_PRIZE`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `REF_GAME_PRIZE` (
  `game_id` varchar(36) NOT NULL,
  `id` varchar(36) NOT NULL,
  `prize_id` varchar(36) NOT NULL,
  `start_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `end_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `quantity` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_game_id` (`game_id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `REF_INSTANTWIN`
--

DROP TABLE IF EXISTS `REF_INSTANTWIN`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `REF_INSTANTWIN` (
  `id` varchar(36) NOT NULL,
  `game_id` varchar(36) NOT NULL,
  `gameprize_id` varchar(36) NOT NULL,
  `prize_id` varchar(36) NOT NULL,
  `activate_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_game_id` (`game_id`,`activate_date`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `REF_PRIZE`
--

DROP TABLE IF EXISTS `REF_PRIZE`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `REF_PRIZE` (
  `id` varchar(36) NOT NULL,
  `country_code` varchar(2) NOT NULL,
  `type` varchar(10) NOT NULL,
  `label` varchar(255) NOT NULL,
  `title` varchar(255) DEFAULT NULL,
  `description` text,
  `picture` text,
  `vendor_code` varchar(20) DEFAULT NULL,
  `face_value` int(11) DEFAULT NULL,
  `points` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2018-02-12 15:11:38