#!/usr/bin/env python3
"""Writes app/src/main/res/values/strings_chromium.xml from Chromium's string files.

Foss Explorer shows Chromium's settings texts. They are not typed in by hand:
this script reads them from a Chromium checkout and writes them as Android
string resources, with "Chrome" replaced by "Explorer".

    python3 tools/chromium_strings.py /path/to/chromium/src

The texts of release 13.2.2 come from Chromium commit
f0407d708267c4ff6a8c16a7da873d020b68f28b (version 160.0.8161.0).

Own code of Foss Explorer, GPL-3.0. The texts it copies are Chromium's
(BSD, third_party/chromium-settings/LICENSE).
"""
import html
import os
import re
import sys

GRD_FILES = [
    'chrome/browser/ui/android/strings/android_chrome_strings.grd',
    'components/browser_ui/strings/android/browser_ui_strings.grd',
    'components/browser_ui/strings/android/site_settings.grdp',
    'chrome/android/features/tab_ui/java/strings/android_chrome_tab_ui_strings.grd',
    'components/new_or_sad_tab_strings.grdp',
]

OUTPUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                      'app', 'src', 'main', 'res', 'values', 'strings_chromium.xml')


def load(root):
    """All messages of the string files: lower-case name without IDS_ -> text."""
    d = {}
    for fn in [os.path.join(root, f) for f in GRD_FILES]:
        if not os.path.exists(fn):
            sys.exit('Missing: ' + fn)
        s = open(fn, encoding='utf-8').read()
        for m in re.finditer(r'<message\s+name\s*=\s*"IDS_([A-Z0-9_]+)"([^>]*)>(.*?)</message>', s, re.S):
            body = m.group(3)
            body = re.sub(r'<ex>.*?</ex>', '', body, flags=re.S)
            body = re.sub(r'<ph name="([A-Z0-9_]+)">(.*?)</ph>',
                          lambda x: '{' + x.group(1) + '|' + x.group(2).strip() + '}', body, flags=re.S)
            body = html.unescape(re.sub(r'\s+', ' ', body).strip())
            if body.startswith("''" + "'"):
                body = body[3:]
            d.setdefault(m.group(1).lower(), body)
    return d


# Name in the app -> name in Chromium (IDS_ left out, lower case).
M = {
 # privacy and security
 'cr_privacy_section_title':'privacy_section_title','cr_security_section_title':'security_section_title',
 'cr_clear_browsing_data_title':'clear_browsing_data_title','cr_clear_browsing_data_summary':'clear_browsing_data_summary',
 'cr_preload_pages_title':'preload_pages_title','cr_preload_pages_summary':'preload_pages_summary',
 'cr_preload_none_title':'preload_pages_no_preloading_title','cr_preload_none_summary':'preload_pages_no_preloading_summary',
 'cr_preload_standard_title':'preload_pages_standard_preloading_title',
 'cr_incognito_lock_title':'settings_incognito_tab_lock_title','cr_incognito_lock_summary_on':'settings_incognito_tab_lock_summary_android_setting_on',
 'cr_incognito_lock_summary_off':'settings_incognito_tab_lock_summary_android_setting_off',
 'cr_incognito_unlock':'incognito_reauth_page_unlock_incognito_button_label','cr_incognito_see_other_tabs':'incognito_reauth_page_see_other_tabs_label',
 'cr_safe_browsing_title':'prefs_safe_browsing_title','cr_safe_browsing_summary_on':'prefs_safe_browsing_summary','cr_safe_browsing_summary_off':'prefs_safe_browsing_no_protection_summary',
 'cr_safe_browsing_standard_title':'safe_browsing_standard_protection_title','cr_safe_browsing_none_title':'safe_browsing_no_protection_title',
 'cr_safe_browsing_off_dialog_title':'safe_browsing_no_protection_confirmation_dialog_title','cr_safe_browsing_off_dialog_message':'safe_browsing_no_protection_confirmation_dialog_message','cr_safe_browsing_off_dialog_confirm':'safe_browsing_no_protection_confirmation_dialog_confirm',
 'cr_js_optimizer_title':'website_settings_javascript_optimizer_link_row_label','cr_js_optimizer_description':'website_settings_category_javascript_optimizer_page_description',
 'cr_js_optimizer_allowed':'website_settings_javascript_optimizer_allowed','cr_js_optimizer_blocked':'website_settings_javascript_optimizer_blocked',
 
 # site settings
 'cr_site_location_description':'website_settings_location_page_description','cr_site_location_on':'website_settings_location_ask','cr_site_location_off':'website_settings_location_block',
 'cr_site_camera_description':'website_settings_camera_page_description','cr_site_camera_on':'website_settings_camera_ask','cr_site_camera_off':'website_settings_camera_block','cr_site_camera_off_description':'website_settings_camera_block_description',
 'cr_site_mic_description':'website_settings_mic_page_description','cr_site_mic_on':'website_settings_mic_ask','cr_site_mic_off':'website_settings_mic_block','cr_site_mic_off_description':'website_settings_mic_block_description',
 'cr_site_loopback_description':'website_settings_loopback_network_page_description','cr_site_loopback_off':'website_settings_loopback_network_block',
 'cr_site_cookies_description':'website_settings_third_party_cookies_page_description','cr_site_cookies_on':'website_settings_third_party_cookies_page_allow_radio_label','cr_site_cookies_off':'website_settings_third_party_cookies_page_block_radio_label','cr_site_cookies_off_description':'website_settings_third_party_cookies_page_block_radio_sub_label','cr_site_cookies_add':'website_settings_third_party_cookies_page_add_allow_exception_description',
 'cr_site_javascript_description':'website_settings_javascript_page_description','cr_site_javascript_on':'website_settings_javascript_allow','cr_site_javascript_off':'website_settings_javascript_block','cr_site_javascript_add_allow':'website_settings_add_site_description_javascript_allow','cr_site_javascript_add_block':'website_settings_add_site_description_javascript_block',
 'cr_site_popups_description':'website_settings_popups_page_description','cr_site_popups_on':'website_settings_popups_redirects_allow','cr_site_popups_off':'website_settings_popups_redirects_block',
 'cr_site_sound_description':'website_settings_sound_page_description','cr_site_sound_on':'website_settings_sound_allow','cr_site_sound_off':'website_settings_sound_block','cr_site_sound_off_description':'website_settings_sound_block_description','cr_site_sound_add_allow':'website_settings_add_site_description_sound_allow','cr_site_sound_add_block':'website_settings_add_site_description_sound_block','cr_site_muted_heading':'website_settings_blocked_group_heading_sound',
 'cr_site_ads_description':'website_settings_ads_page_description','cr_site_ads_on':'site_settings_page_intrusive_allowed_sub_label',
 'cr_site_protected_description':'website_settings_protected_content_page_description','cr_site_protected_allow':'website_settings_protected_content_allow','cr_site_protected_ask':'website_settings_protected_content_ask','cr_site_protected_block':'website_settings_protected_content_block',
 'cr_site_data_description':'website_settings_site_data_page_description','cr_site_data_on':'website_settings_site_data_page_toggle_sub_label_allow','cr_site_data_off':'website_settings_site_data_page_toggle_sub_label_block','cr_site_data_block_title':'website_settings_site_data_page_block_confirm_dialog_title','cr_site_data_block_message':'website_settings_site_data_page_block_confirm_dialog_description','cr_site_data_block_confirm':'website_settings_site_data_page_block_confirm_dialog_confirm_button',
 'cr_site_data_add_allow':'website_settings_site_data_page_add_allow_exception_description','cr_site_data_add_block':'website_settings_site_data_page_add_block_exception_description',
 'cr_site_downloads_description':'website_settings_automatic_downloads_page_description','cr_site_downloads_on':'website_settings_automatic_downloads_ask','cr_site_downloads_off':'website_settings_automatic_downloads_block','cr_site_downloads_add':'website_settings_add_site_description_automatic_downloads',
 'cr_site_auto_verify_description':'anti_abuse_description','cr_site_auto_verify_when_on':'anti_abuse_when_on_header','cr_site_auto_verify_on_1':'anti_abuse_when_on_section_one','cr_site_auto_verify_on_2':'anti_abuse_when_on_section_two','cr_site_auto_verify_on_3':'anti_abuse_when_on_section_three','cr_site_auto_verify_consider':'anti_abuse_things_to_consider_header','cr_site_auto_verify_consider_1':'anti_abuse_things_to_consider_section_one',
 'cr_site_allowed_heading':'website_settings_allowed_group_heading','cr_site_not_allowed_heading':'website_settings_not_allowed_group_heading',
 'cr_site_add':'website_settings_add_site','cr_site_add_dialog_title':'website_settings_add_site_dialog_title','cr_site_add_button':'website_settings_add_site_add_button','cr_site_add_url':'website_settings_add_site_site_url','cr_site_add_toast':'website_settings_add_site_toast',
 # accessibility
 'cr_page_zoom_title':'page_zoom_title','cr_page_zoom_summary':'page_zoom_summary','cr_page_zoom_level':'page_zoom_level','cr_page_zoom_label':'page_zoom_label','cr_page_zoom_menu_title':'page_zoom_menu_title',
 'cr_page_zoom_decrease':'page_zoom_decrease_zoom_button_text','cr_page_zoom_increase':'page_zoom_increase_zoom_button_text',
 'cr_page_zoom_preview_title':'page_zoom_preview_title','cr_page_zoom_preview_text_title':'page_zoom_preview_text_title','cr_page_zoom_preview_text_summary':'page_zoom_preview_text_summary','cr_page_zoom_preview_text_item':'page_zoom_preview_text_item',
 'cr_zoom_info_title':'zoom_info_preference_title','cr_zoom_info_empty':'site_settings_empty_zoom_levels_message','cr_zoom_info_clear_all':'site_settings_clear_all_zoom_levels_button_text','cr_zoom_info_clear_all_warning':'site_settings_clear_all_zoom_levels_warning',
 'cr_page_zoom_os_title':'page_zoom_include_os_adjustment_title','cr_page_zoom_os_summary':'page_zoom_include_os_adjustment_summary',
 'cr_page_zoom_always_show_title':'page_zoom_always_show_preference_title','cr_page_zoom_always_show_summary':'page_zoom_always_show_preference_summary',
 'cr_force_enable_zoom_title':'force_enable_zoom_title','cr_force_enable_zoom_summary':'force_enable_zoom_summary',
 'cr_reader_title':'reader_for_accessibility_title','cr_reader_summary':'reader_for_accessibility_summary','cr_reader_links_title':'reader_enable_links_title','cr_reader_links_summary':'reader_enable_links_summary',
 'cr_reader_message_title':'reader_mode_message_title','cr_reader_message_button':'reader_mode_message_button',
 'cr_caret_browsing_title':'caret_browsing_title',
 'cr_captions_title':'accessibility_captions_title','cr_image_descriptions_title':'image_descriptions_settings_title',
 'cr_touchpad_title':'touchpad_overscroll_history_navigation_title','cr_touchpad_summary':'touchpad_overscroll_history_navigation_summary',
 # tabs
 'cr_archive_title':'archive_settings_title','cr_archive_section_title':'archive_settings_section_title',
 'cr_archive_never':'archive_settings_time_delta_never','cr_archive_7':'archive_settings_time_delta_7_days','cr_archive_14':'archive_settings_time_delta_14_days','cr_archive_21':'archive_settings_time_delta_21_days',
 'cr_archive_duplicates_title':'archive_settings_archive_duplicate_tabs_title',
 'cr_archive_autodelete_title':'archive_settings_allow_autodelete_title',
 # the menu of a long press on a link or a picture
 'cr_contextmenu_open_in_new_tab':'contextmenu_open_in_new_tab','cr_contextmenu_open_in_incognito_tab':'contextmenu_open_in_incognito_tab',
 'cr_contextmenu_copy_link_address':'contextmenu_copy_link_address','cr_contextmenu_copy_link_text':'contextmenu_copy_link_text',
 'cr_contextmenu_save_link':'contextmenu_save_link','cr_contextmenu_share_link':'contextmenu_share_link',
 'cr_contextmenu_open_image_in_new_tab':'contextmenu_open_image_in_new_tab','cr_contextmenu_copy_image':'contextmenu_copy_image',
 'cr_contextmenu_save_image':'contextmenu_save_image','cr_contextmenu_share_image':'contextmenu_share_image',
 # the tab switcher and the new tab page of a private tab
 'cr_hub_search_hint':'hub_search_empty_hint','cr_hub_search_hint_incognito':'hub_search_empty_hint_incognito',
 'cr_incognito_ntp_title':'new_tab_otr_title','cr_incognito_ntp_subtitle':'new_tab_otr_subtitle',
 # about
 'cr_application_version_title':'application_version_title','cr_os_version_title':'os_version_title','cr_legal_information_title':'legal_information_title',
 
}


def conv(v):
    """A Chromium message as the text of an Android string resource."""
    v = re.sub(r'\{[A-Z0-9_]+\|(.*?)\}', r'\1', v)   # {NAME|%1$s} -> %1$s
    v = re.sub(r'</?link[^>]*>', '', v)
    v = v.replace('Chrome', 'Explorer')
    v = re.sub(r'\s*Learn more$', '', v.strip())     # the link behind it leads to Google's help pages
    v = v.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    return v.replace("'", "\\'").replace('"', '\\"')


HEADER = """<?xml version="1.0" encoding="utf-8"?>
<!--
  Texts of the settings screens, taken from Chromium (commit f0407d708267c4ff6a8c16a7da873d020b68f28b,
  version 160.0.8161.0): chrome/browser/ui/android/strings/android_chrome_strings.grd,
  components/browser_ui/strings/android/browser_ui_strings.grd, site_settings.grdp and
  chrome/android/features/tab_ui/java/strings/android_chrome_tab_ui_strings.grd and
  components/new_or_sad_tab_strings.grdp.
  "Chrome" is replaced by "Explorer"; placeholders and link marks are adapted to Android resources.

  Copyright The Chromium Authors. Use of this source code is governed by a BSD-style license
  that can be found in third_party/chromium-settings/LICENSE.

  Written by tools/chromium_strings.py. Do not edit by hand.
-->
<resources xmlns:tools="http://schemas.android.com/tools" tools:ignore="MissingTranslation">
"""


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    messages = load(sys.argv[1])
    missing = [src for src in M.values() if src not in messages]
    if missing:
        sys.exit('Not found in the string files: ' + ', '.join(missing))
    lines = ['    <string name="%s">%s</string>' % (name, conv(messages[src])) for name, src in M.items()]
    with open(OUTPUT, 'w', encoding='utf-8') as out:
        out.write(HEADER + '\n'.join(lines) + '\n</resources>\n')
    print('%d strings written to %s' % (len(lines), os.path.normpath(OUTPUT)))


if __name__ == '__main__':
    main()
