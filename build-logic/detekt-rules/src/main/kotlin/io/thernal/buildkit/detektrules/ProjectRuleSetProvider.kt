package io.thernal.buildkit.detektrules

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider
import io.thernal.buildkit.detektrules.collections.UnsafeCollectionIndexAccess
import io.thernal.buildkit.detektrules.packageboundary.LayerPackageBoundary
import io.thernal.buildkit.detektrules.packageboundary.LayerPackageRequired
import io.thernal.buildkit.detektrules.preview.PreviewMustBePrivate
import io.thernal.buildkit.detektrules.style.ExpressionBodyNotAllowed
import io.thernal.buildkit.detektrules.style.MultilineConstructorRequired

class ProjectRuleSetProvider : RuleSetProvider {
    override val ruleSetId = RuleSetId("project")

    override fun instance() = RuleSet(
        ruleSetId,
        listOf(
            ::PreviewMustBePrivate,
            ::UnsafeCollectionIndexAccess,
            ::LayerPackageBoundary,
            ::LayerPackageRequired,
            ::ExpressionBodyNotAllowed,
            ::MultilineConstructorRequired,
        ),
    )
}
