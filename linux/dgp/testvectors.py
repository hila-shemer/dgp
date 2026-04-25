from dataclasses import dataclass

from dgp.engine import generate

_A64 = "A" * 64
_A65 = "A" * 65
_B64 = "B" * 64
_B65 = "B" * 65
_P = "passwordPASSWORDpassword"
_S = "saltSALTsaltSALTsaltSALTsaltSALTsalt"


@dataclass(frozen=True)
class TestVector:
    seed: str
    account: str
    name: str
    type: str
    expected: str


VECTORS: list[TestVector] = [
    # 4 basic
    TestVector("a", "", "aa", "alnum", "oxToKKV2"),
    TestVector("aa", "", "a", "alnum", "g5ELZ6hf"),
    TestVector("a", "", "aa", "base58", "zWNoxToK"),
    TestVector("a", "", "aa", "alnumlong", "zWNoxToKKV2j"),
    # all types: seed=passwordPASSWORDpassword, account="", name=saltSALT...
    TestVector(_P, "", _S, "hex", "21934584"),
    TestVector(_P, "", _S, "hexlong", "21934584b8ffde2e"),
    TestVector(_P, "", _S, "alnum", "1Yir2cqz"),
    TestVector(_P, "", _S, "alnumlong", "1Yir2cqzSZNb"),
    TestVector(_P, "", _S, "base58", "1Yir2cqz"),
    TestVector(_P, "", _S, "base58long", "1Yir2cqzSZNb"),
    TestVector(_P, "", _S, "xkcd", "OrdinaryReadyIcePower"),
    TestVector(_P, "", _S, "xkcdlong", "OrdinaryReadyIcePowerVerbStill"),
    # all types: seed=pass, account=word, name=salt
    TestVector("pass", "word", "salt", "hex", "842b8a86"),
    TestVector("pass", "word", "salt", "hexlong", "842b8a866ef6f789"),
    TestVector("pass", "word", "salt", "alnum", "HUgR5fny"),
    TestVector("pass", "word", "salt", "alnumlong", "HUgR5fnynuHv"),
    TestVector("pass", "word", "salt", "base58", "HUgR5fny"),
    TestVector("pass", "word", "salt", "base58long", "HUgR5fnynuHv"),
    TestVector("pass", "word", "salt", "xkcd", "StemDialSureHen"),
    TestVector("pass", "word", "salt", "xkcdlong", "StemDialSureHenAlbumDonor"),
    # 6×3 with account=""
    TestVector(_A64, "", "salt", "hexlong", "58c3f208e6ddf020"),
    TestVector(_A64, "", "salt", "alnum", "PARqw6dE"),
    TestVector(_A64, "", "salt", "xkcdlong", "DivorceAddressHintRuralViciousPeasant"),
    TestVector(_A65, "", "salt", "hexlong", "9d63c79fabd80bc0"),
    TestVector(_A65, "", "salt", "alnum", "zcXridY3"),
    TestVector(_A65, "", "salt", "xkcdlong", "ButterTrendPupilTreeSettleWave"),
    TestVector(_A64, "", _B64, "hexlong", "2a8702b185b36310"),
    TestVector(_A64, "", _B64, "alnum", "7Pr3ECx2"),
    TestVector(_A64, "", _B64, "xkcdlong", "DynamicVacantReadyRifleGuardDestroy"),
    TestVector(_A64, "", _B65, "hexlong", "9c414dc4add39239"),
    TestVector(_A64, "", _B65, "alnum", "7oBwChSZ"),
    TestVector(_A64, "", _B65, "xkcdlong", "ShrugCargoKeyFollowAcousticPole"),
    TestVector(_A65, "", _B64, "hexlong", "2d92337b38d9279e"),
    TestVector(_A65, "", _B64, "alnum", "qt7hBsti"),
    TestVector(_A65, "", _B64, "xkcdlong", "DisagreePersonBeginConductGeniusMemory"),
    TestVector(_A65, "", _B65, "hexlong", "d253b247c80be550"),
    TestVector(_A65, "", _B65, "alnum", "entN2FVV"),
    TestVector(_A65, "", _B65, "xkcdlong", "SquareBehaveDawnToiletUnknownAddict"),
    # 6×3 with account="default"
    TestVector(_A64, "default", "salt", "hexlong", "631817232100b665"),
    TestVector(_A64, "default", "salt", "alnum", "Aqvbp2ce"),
    TestVector(_A64, "default", "salt", "xkcdlong", "AutumnLeopardDonkeyAcquireKeepOrient"),
    TestVector(_A65, "default", "salt", "hexlong", "90cee7907f9992c3"),
    TestVector(_A65, "default", "salt", "alnum", "DfWGSPC4"),
    TestVector(_A65, "default", "salt", "xkcdlong", "YoungProudMonitorBabyGaugeCrisp"),
    TestVector(_A64, "default", _B64, "hexlong", "f7df3aa86e825f90"),
    TestVector(_A64, "default", _B64, "alnum", "yYC6LKxw"),
    TestVector(_A64, "default", _B64, "xkcdlong", "DebateBlessWisdomWillPostSlim"),
    TestVector(_A64, "default", _B65, "hexlong", "784ee7adec0a3d53"),
    TestVector(_A64, "default", _B65, "alnum", "uCN95mmQ"),
    TestVector(_A64, "default", _B65, "xkcdlong", "GospelDoctorSpiderFrostFleeRequire"),
    TestVector(_A65, "default", _B64, "hexlong", "a556da50eca68f48"),
    TestVector(_A65, "default", _B64, "alnum", "mTNf5LBU"),
    TestVector(_A65, "default", _B64, "xkcdlong", "TipMatrixWhatRevealDinnerObject"),
    TestVector(_A65, "default", _B65, "hexlong", "92657b613e27055d"),
    TestVector(_A65, "default", _B65, "alnum", "G8caSAgC"),
    TestVector(_A65, "default", _B65, "xkcdlong", "PioneerSnowTreeToiletWrongOblige"),
    # 6×3 with account="test"
    TestVector(_A64, "test", "salt", "hexlong", "44e62d0c3c6e8c4c"),
    TestVector(_A64, "test", "salt", "alnum", "2aQahrAd"),
    TestVector(_A64, "test", "salt", "xkcdlong", "SideSplitEasyStandKetchupTry"),
    TestVector(_A65, "test", "salt", "hexlong", "54bd33e6779b3570"),
    TestVector(_A65, "test", "salt", "alnum", "mTjWwoK8"),
    TestVector(_A65, "test", "salt", "xkcdlong", "EnjoyExtraWheelThrowBeginCompany"),
    TestVector(_A64, "test", _B64, "hexlong", "7709c029647ebdcb"),
    TestVector(_A64, "test", _B64, "alnum", "v22zGD6C"),
    TestVector(_A64, "test", _B64, "xkcdlong", "VoiceKidDealOffModifyFinal"),
    TestVector(_A64, "test", _B65, "hexlong", "67a25500a88e1193"),
    TestVector(_A64, "test", _B65, "alnum", "Sawi6Lby"),
    TestVector(_A64, "test", _B65, "xkcdlong", "EmbodyActualAssetBoxChampionAnchor"),
    TestVector(_A65, "test", _B64, "hexlong", "46ce8f41a45c2fd6"),
    TestVector(_A65, "test", _B64, "alnum", "hDaVCKY9"),
    TestVector(_A65, "test", _B64, "xkcdlong", "IssueArgueSpaceSegmentCasualPlate"),
    TestVector(_A65, "test", _B65, "hexlong", "c84856500ca1d4b1"),
    TestVector(_A65, "test", _B65, "alnum", "49RT52Ed"),
    TestVector(_A65, "test", _B65, "xkcdlong", "PropertyNetCrossDangerLaughPepper"),
]


@dataclass(frozen=True)
class SingleResult:
    label: str
    expected: str
    actual: str
    passed: bool


def run_one(idx: int) -> SingleResult:
    tv = VECTORS[idx]
    actual = generate(tv.seed, tv.name, tv.type, tv.account)
    passed = actual == tv.expected
    seed_disp = f"{tv.seed[:4]}...({len(tv.seed)})" if len(tv.seed) > 16 else tv.seed
    name_disp = f"{tv.name[:4]}...({len(tv.name)})" if len(tv.name) > 16 else tv.name
    label = f"{seed_disp}:{tv.account}:{name_disp}:{tv.type}"
    return SingleResult(label, tv.expected, actual, passed)


def run_all() -> list[SingleResult]:
    return [run_one(i) for i in range(len(VECTORS))]
