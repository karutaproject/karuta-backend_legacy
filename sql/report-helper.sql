-- Table creation only

--
-- Structure de la table `log`
--
CREATE TABLE IF NOT EXISTS `vector_table` (
  `userid` bigint(20) NOT NULL,
  `date` datetime NOT NULL DEFAULT NOW(),
  `a1` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT "",
  `a2` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT "",
  `a3` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT "",
  `a4` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT "",
  `a5` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT "",
  `a6` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT "",
  `a7` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT "",
  `a8` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT "",
  `a9` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT "",
  `a10` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT ""
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--
-- Contenu de la table `log`
--

