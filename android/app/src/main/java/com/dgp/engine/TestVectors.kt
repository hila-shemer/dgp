package com.dgp.engine

data class TestVector(
    val seed: String,
    val account: String,
    val name: String,
    val type: String,
    val expected: String
)

object TestVectors {

    val vectors: List<TestVector> = buildList {
        // Basic vectors
        add(TestVector("a", "", "aa", "alnum", "oxToKKV2"))
        add(TestVector("aa", "", "a", "alnum", "g5ELZ6hf"))
        add(TestVector("a", "", "aa", "base58", "zWNoxToK"))
        add(TestVector("a", "", "aa", "alnumlong", "zWNoxToKKV2j"))

        // All types: seed=passwordPASSWORDpassword, account="", name=saltSALT...
        val p = "passwordPASSWORDpassword"
        val s = "saltSALTsaltSALTsaltSALTsaltSALTsalt"
        add(TestVector(p, "", s, "hex", "21934584"))
        add(TestVector(p, "", s, "hexlong", "21934584b8ffde2e"))
        add(TestVector(p, "", s, "alnum", "1Yir2cqz"))
        add(TestVector(p, "", s, "alnumlong", "1Yir2cqzSZNb"))
        add(TestVector(p, "", s, "base58", "1Yir2cqz"))
        add(TestVector(p, "", s, "base58long", "1Yir2cqzSZNb"))
        add(TestVector(p, "", s, "xkcd", "OrdinaryReadyIcePower"))
        add(TestVector(p, "", s, "xkcdlong", "OrdinaryReadyIcePowerVerbStill"))

        // All types: seed=pass, account=word, name=salt
        add(TestVector("pass", "word", "salt", "hex", "842b8a86"))
        add(TestVector("pass", "word", "salt", "hexlong", "842b8a866ef6f789"))
        add(TestVector("pass", "word", "salt", "alnum", "HUgR5fny"))
        add(TestVector("pass", "word", "salt", "alnumlong", "HUgR5fnynuHv"))
        add(TestVector("pass", "word", "salt", "base58", "HUgR5fny"))
        add(TestVector("pass", "word", "salt", "base58long", "HUgR5fnynuHv"))
        add(TestVector("pass", "word", "salt", "xkcd", "StemDialSureHen"))
        add(TestVector("pass", "word", "salt", "xkcdlong", "StemDialSureHenAlbumDonor"))

        val a64 = "A".repeat(64)
        val a65 = "A".repeat(65)
        val b64 = "B".repeat(64)
        val b65 = "B".repeat(65)

        // some_types with no account
        for ((seed, name, hlExp, alExp, xlExp) in listOf(
            Triple5(a64, "salt", "58c3f208e6ddf020", "PARqw6dE", "DivorceAddressHintRuralViciousPeasant"),
            Triple5(a65, "salt", "9d63c79fabd80bc0", "zcXridY3", "ButterTrendPupilTreeSettleWave"),
            Triple5(a64, b64, "2a8702b185b36310", "7Pr3ECx2", "DynamicVacantReadyRifleGuardDestroy"),
            Triple5(a64, b65, "9c414dc4add39239", "7oBwChSZ", "ShrugCargoKeyFollowAcousticPole"),
            Triple5(a65, b64, "2d92337b38d9279e", "qt7hBsti", "DisagreePersonBeginConductGeniusMemory"),
            Triple5(a65, b65, "d253b247c80be550", "entN2FVV", "SquareBehaveDawnToiletUnknownAddict"),
        )) {
            add(TestVector(seed, "", name, "hexlong", hlExp))
            add(TestVector(seed, "", name, "alnum", alExp))
            add(TestVector(seed, "", name, "xkcdlong", xlExp))
        }

        // some_types with account=default
        for ((seed, name, hlExp, alExp, xlExp) in listOf(
            Triple5(a64, "salt", "631817232100b665", "Aqvbp2ce", "AutumnLeopardDonkeyAcquireKeepOrient"),
            Triple5(a65, "salt", "90cee7907f9992c3", "DfWGSPC4", "YoungProudMonitorBabyGaugeCrisp"),
            Triple5(a64, b64, "f7df3aa86e825f90", "yYC6LKxw", "DebateBlessWisdomWillPostSlim"),
            Triple5(a64, b65, "784ee7adec0a3d53", "uCN95mmQ", "GospelDoctorSpiderFrostFleeRequire"),
            Triple5(a65, b64, "a556da50eca68f48", "mTNf5LBU", "TipMatrixWhatRevealDinnerObject"),
            Triple5(a65, b65, "92657b613e27055d", "G8caSAgC", "PioneerSnowTreeToiletWrongOblige"),
        )) {
            add(TestVector(seed, "default", name, "hexlong", hlExp))
            add(TestVector(seed, "default", name, "alnum", alExp))
            add(TestVector(seed, "default", name, "xkcdlong", xlExp))
        }

        // some_types with account=test
        for ((seed, name, hlExp, alExp, xlExp) in listOf(
            Triple5(a64, "salt", "44e62d0c3c6e8c4c", "2aQahrAd", "SideSplitEasyStandKetchupTry"),
            Triple5(a65, "salt", "54bd33e6779b3570", "mTjWwoK8", "EnjoyExtraWheelThrowBeginCompany"),
            Triple5(a64, b64, "7709c029647ebdcb", "v22zGD6C", "VoiceKidDealOffModifyFinal"),
            Triple5(a64, b65, "67a25500a88e1193", "Sawi6Lby", "EmbodyActualAssetBoxChampionAnchor"),
            Triple5(a65, b64, "46ce8f41a45c2fd6", "hDaVCKY9", "IssueArgueSpaceSegmentCasualPlate"),
            Triple5(a65, b65, "c84856500ca1d4b1", "49RT52Ed", "PropertyNetCrossDangerLaughPepper"),
        )) {
            add(TestVector(seed, "test", name, "hexlong", hlExp))
            add(TestVector(seed, "test", name, "alnum", alExp))
            add(TestVector(seed, "test", name, "xkcdlong", xlExp))
        }
    }

    data class Triple5(val a: String, val b: String, val c: String, val d: String, val e: String)

    fun run(engine: DgpEngine): TestResult {
        val lines = mutableListOf<String>()
        var passed = 0
        var failed = 0

        for (tv in vectors) {
            val actual = engine.generate(tv.seed, tv.name, tv.type, tv.account)
            if (actual == tv.expected) {
                passed++
            } else {
                failed++
                val seedDisp = if (tv.seed.length > 16) "${tv.seed.take(8)}...(${tv.seed.length})" else tv.seed
                val nameDisp = if (tv.name.length > 16) "${tv.name.take(8)}...(${tv.name.length})" else tv.name
                lines.add("FAIL [$seedDisp:${tv.account}:$nameDisp:${tv.type}]")
                lines.add("  expected: ${tv.expected}")
                lines.add("  actual:   $actual")
            }
        }

        lines.add(0, "$passed passed, $failed failed out of ${vectors.size} tests")
        return TestResult(passed, failed, lines.joinToString("\n"))
    }

    data class TestResult(val passed: Int, val failed: Int, val output: String) {
        val allPassed get() = failed == 0
    }
}
