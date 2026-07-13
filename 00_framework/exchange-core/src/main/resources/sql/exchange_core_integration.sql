CREATE TABLE IF NOT EXISTS `exchange_core_symbol_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `symbol` varchar(32) NOT NULL,
  `core_symbol_id` int NOT NULL,
  `base_symbol` varchar(16) NOT NULL,
  `quote_symbol` varchar(16) NOT NULL,
  `base_currency_id` int NOT NULL,
  `quote_currency_id` int NOT NULL,
  `base_scale` int NOT NULL,
  `quote_scale` int NOT NULL,
  `base_scale_k` bigint NOT NULL,
  `quote_scale_k` bigint NOT NULL,
  `maker_fee` bigint DEFAULT 0,
  `taker_fee` bigint DEFAULT 0,
  `status` int DEFAULT 0,
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exchange_core_symbol` (`symbol`),
  UNIQUE KEY `uk_exchange_core_symbol_id` (`core_symbol_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `exchange_core_order_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` varchar(64) NOT NULL,
  `core_order_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `symbol` varchar(32) NOT NULL,
  `core_symbol_id` int DEFAULT NULL,
  `direction` int DEFAULT NULL,
  `type` int DEFAULT NULL,
  `status` int DEFAULT NULL,
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exchange_core_order_id` (`order_id`),
  UNIQUE KEY `uk_exchange_core_core_order_id` (`core_order_id`),
  KEY `idx_exchange_core_order_member` (`member_id`),
  KEY `idx_exchange_core_order_symbol` (`symbol`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `exchange_core_command_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `command_id` varchar(96) NOT NULL,
  `command_type` varchar(32) NOT NULL,
  `business_key` varchar(128) DEFAULT NULL,
  `payload` text,
  `status` varchar(24) NOT NULL,
  `result_code` varchar(64) DEFAULT NULL,
  `error_message` varchar(1024) DEFAULT NULL,
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exchange_core_command_id` (`command_id`),
  KEY `idx_exchange_core_command_business` (`business_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `exchange_core_trade_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `trade_id` varchar(96) NOT NULL,
  `core_seq` bigint NOT NULL,
  `event_type` varchar(32) NOT NULL DEFAULT 'TRADE',
  `symbol` varchar(32) NOT NULL,
  `core_symbol_id` int DEFAULT NULL,
  `maker_order_id` varchar(64) DEFAULT NULL,
  `taker_order_id` varchar(64) DEFAULT NULL,
  `maker_member_id` bigint DEFAULT NULL,
  `taker_member_id` bigint DEFAULT NULL,
  `price` decimal(26,16) DEFAULT 0,
  `amount` decimal(26,16) DEFAULT 0,
  `turnover` decimal(26,16) DEFAULT 0,
  `maker_fee` decimal(26,16) DEFAULT 0,
  `taker_fee` decimal(26,16) DEFAULT 0,
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exchange_core_trade_id` (`trade_id`),
  KEY `idx_exchange_core_trade_symbol` (`symbol`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `exchange_core_balance_mirror` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `currency` varchar(16) NOT NULL,
  `core_currency_id` int DEFAULT NULL,
  `available` decimal(26,16) DEFAULT 0,
  `reserved` decimal(26,16) DEFAULT 0,
  `total` decimal(26,16) DEFAULT 0,
  `core_seq` bigint DEFAULT NULL,
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exchange_core_balance_member_currency` (`member_id`,`currency`),
  KEY `idx_exchange_core_balance_seq` (`core_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `member_trading_transfer_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `command_id` varchar(96) NOT NULL,
  `transfer_type` varchar(32) NOT NULL,
  `member_id` bigint NOT NULL,
  `currency` varchar(16) NOT NULL,
  `core_currency_id` int NOT NULL,
  `amount` decimal(26,16) NOT NULL,
  `status` varchar(24) NOT NULL,
  `result_code` varchar(64) DEFAULT NULL,
  `error_message` varchar(1024) DEFAULT NULL,
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_trading_transfer_command` (`command_id`),
  KEY `idx_member_trading_transfer_member` (`member_id`,`currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
