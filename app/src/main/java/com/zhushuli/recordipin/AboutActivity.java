package com.zhushuli.recordipin;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.text.HtmlCompat;

import android.os.Bundle;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

/**
 * @author : zhushuli
 * @createDate : 2024/01/12 11:00
 * @description : Refer to https://github.com/A3DV/VIRec
 */
public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        TextView creator = findViewById(R.id.tvCreator);
        creator.setMovementMethod(LinkMovementMethod.getInstance());

        TextView hyperlink = findViewById(R.id.linkTextView);
        String linkText = getResources().getString(R.string.link_foreword);

        Spanned text = HtmlCompat.fromHtml(linkText + " " + "<a href='https://github.com/Juderer/RecordIPIN'>GitHub</a>.",
                HtmlCompat.FROM_HTML_OPTION_USE_CSS_COLORS);
        hyperlink.setMovementMethod(LinkMovementMethod.getInstance());
        hyperlink.setText(text);

        TextView versionName = findViewById(R.id.versionText);
        versionName.setText(getString(R.string.versionName, BuildConfig.VERSION_NAME));
    }
}