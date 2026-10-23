package dev.egateza.termul.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.egateza.termul.core.profile.EnvironmentTag;
import org.junit.jupiter.api.Test;

class ProfileDialogSensitiveTest {

    @Test
    void samaDenganDefaultEnvironmentDisimpanNullSupayaTetapIkutEnvironment() {
        assertThat(ProfileDialog.sensitiveValue(false, true, EnvironmentTag.PROD)).isNull();
        assertThat(ProfileDialog.sensitiveValue(false, false, EnvironmentTag.DEV)).isNull();
        assertThat(ProfileDialog.sensitiveValue(true, true, EnvironmentTag.PROD)).isNull();
        assertThat(ProfileDialog.sensitiveValue(true, false, EnvironmentTag.STAGING)).isNull();
    }

    @Test
    void pilihanUserYangBedaDariDefaultDisimpanEksplisit() {
        assertThat(ProfileDialog.sensitiveValue(true, true, EnvironmentTag.STAGING)).isTrue();
        assertThat(ProfileDialog.sensitiveValue(true, false, EnvironmentTag.PROD)).isFalse();
    }
}
