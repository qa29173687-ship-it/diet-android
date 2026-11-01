package tw.dietbook

/**
 * 每日建議：目標是「每天維持健康熱量缺口，但攝取不要低於 BMR」。
 *
 * 帳戶語意：balance &lt; 0 = 缺口／存款；balance &gt; 0 = 熱量進帳（吃太多）。
 * 使用者說的「存款變負／要注意」對應進帳（surplus）。
 */
enum class AdviceKind { BELOW_BMR, SURPLUS_CARE, PRAISE }

data class AdviceCard(
    val kind: AdviceKind,
    val title: String,
    val body: String,
)

object DailyAdvice {
    fun of(intakeKcal: Double, bmr: Double, balanceKcal: Double): AdviceCard = when {
        intakeKcal < bmr -> belowBmr(bmr)
        balanceKcal > 0.0 -> surplusCare()
        else -> praise()
    }

    private fun belowBmr(bmr: Double): AdviceCard = AdviceCard(
        kind = AdviceKind.BELOW_BMR,
        title = "🐷 今天吃得有點少喔",
        body = buildString {
            append("攝取低於基礎代謝（約 ${Calc.r0(bmr)} kcal）時，小豬也會擔心～\n")
            append("長期可能：肌肉流失、代謝變慢、容易疲倦頭暈、營養不夠，")
            append("甚至掉髮或經期紊亂，之後也較容易反彈。\n")
            append("建議至少吃到 BMR，並補足蛋白質。這不是醫療建議，感覺不適請詢問專業人士。")
        },
    )

    private fun surplusCare(): AdviceCard = AdviceCard(
        kind = AdviceKind.SURPLUS_CARE,
        title = "💗 要注意健康喔",
        body = "今天有吃夠基礎代謝，讚！不過熱量還在進帳中。小豬希望你溫柔維持缺口，"
            + "慢慢回到存款狀態就好，別太苛刻自己。這只是一般提醒，不是醫療建議。",
    )

    private fun praise(): AdviceCard = AdviceCard(
        kind = AdviceKind.PRAISE,
        title = "🐷💕 很棒！繼續保持",
        body = "有吃到基礎代謝，又維持健康熱量缺口，小豬幫你拍拍！今天也溫柔地繼續這樣就好 ♡",
    )
}
