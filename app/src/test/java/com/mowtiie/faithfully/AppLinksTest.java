package com.mowtiie.faithfully;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.mowtiie.faithfully.helper.AppLinks;

import org.junit.Test;

public class AppLinksTest {

    @Test
    public void http_and_https_links_pass_through() {
        assertEquals("https://github.com/mowtiie/Faithful", AppLinks.resolve("https://github.com/mowtiie/Faithful"));
        assertEquals("http://example.com/a.apk", AppLinks.resolve("http://example.com/a.apk"));
        assertEquals("https://example.com/a", AppLinks.resolve("  https://example.com/a  "));
    }

    @Test
    public void the_websites_relative_paths_become_site_links() {
        assertEquals("https://alliyannah.love/apps/Faithful.apk", AppLinks.resolve("./apps/Faithful.apk"));
        assertEquals("https://alliyannah.love/icons/faithful.png", AppLinks.resolve("./icons/faithful.png"));
        assertEquals("https://alliyannah.love/icons/a.png", AppLinks.resolve("/icons/a.png"));
        assertEquals("https://alliyannah.love/icons/a.png", AppLinks.resolve("icons/a.png"));
    }

    @Test
    public void empty_links_are_ignored() {
        assertNull(AppLinks.resolve(null));
        assertNull(AppLinks.resolve(""));
        assertNull(AppLinks.resolve("   "));
        assertNull(AppLinks.resolve("./"));
    }

    @Test
    public void other_schemes_and_odd_paths_are_refused() {
        assertNull(AppLinks.resolve("javascript:alert(1)"));
        assertNull(AppLinks.resolve("intent://scan/#Intent;scheme=zxing;end"));
        assertNull(AppLinks.resolve("file:///sdcard/a.apk"));
        assertNull(AppLinks.resolve("//evil.example/a.apk"));
        assertNull(AppLinks.resolve("../../secret"));
    }
}
