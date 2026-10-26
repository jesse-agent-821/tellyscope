// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import app.tellyscope.helper.Labels
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelsTest {
    @Test fun theGenericRobotIsNotAnAppsOwnIcon() {
        // These draw as the green robot: the app shows a letter tile instead.
        assertFalse(Labels.ownIcon(0))
        assertFalse(Labels.ownIcon(android.R.drawable.sym_def_app_icon))
        assertFalse(Labels.ownIcon(android.R.mipmap.sym_def_app_icon))
    }

    @Test fun anIconFromTheAppsOwnResourcesIs() {
        // XGIMI's template robot on a grid is still that app's own icon.
        assertTrue(Labels.ownIcon(0x7f0e0000))
    }
}
