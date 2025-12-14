package org.arnor.extshare

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display

class DisplayTestPresentation(
    context: Context,
    display: Display
) : Presentation(context, display) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.presentation_test)
    }
}
