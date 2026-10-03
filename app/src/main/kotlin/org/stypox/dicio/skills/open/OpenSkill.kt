package org.stypox.dicio.skills.open

import android.annotation.SuppressLint
import android.content.Intent
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.standard.StandardRecognizerData
import org.dicio.skill.standard.StandardRecognizerSkill
import org.stypox.dicio.sentences.Sentences.Open

class OpenSkill(correspondingSkillInfo: SkillInfo, data: StandardRecognizerData<Open>)
    : StandardRecognizerSkill<Open>(correspondingSkillInfo, data) {

    override suspend fun generateOutput(ctx: SkillContext, inputData: Open): SkillOutput {
        val requested = when (inputData) { is Open.Query -> inputData.what?.trim() }
        val pm = ctx.android.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @SuppressLint("QueryPermissionsNeeded")
        val installed = pm.queryIntentActivities(intent, 0).map {
            AppMatcher.App(it.activityInfo.packageName, it.loadLabel(pm).toString())
        }
        val matches = AppMatcher.candidates(requested.orEmpty(), installed)
        val selected = matches.singleOrNull()
        val launch = selected?.let { pm.getLaunchIntentForPackage(it.packageName) }
        if (launch != null) ctx.android.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return OpenOutput(
            appName = selected?.label ?: requested,
            packageName = if (launch != null) selected?.packageName else null,
            choices = if (matches.size > 1) matches else emptyList(),
        )
    }
}
