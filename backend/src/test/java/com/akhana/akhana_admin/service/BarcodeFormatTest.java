package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.service.impl.BarcodeFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** CAT-10: the formats a factory barcode can have. */
class BarcodeFormatTest {

    @Test
    void keepsEan13AndEan8() {
        assertThat(BarcodeFormat.canonicalize("7791234567898")).contains("7791234567898");
        assertThat(BarcodeFormat.canonicalize("96385074")).contains("96385074");
    }

    @Test
    void storesUpcAWithLeadingZero() {
        assertThat(BarcodeFormat.canonicalize("036000291452")).contains("0036000291452");
        assertThat(BarcodeFormat.canonicalize("0036000291452")).contains("0036000291452");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1234567", "7791234567890", "12345678901234", "779123456789A", "96385075"})
    void rejectsOtherLengthsLettersAndWrongCheckDigits(String code) {
        assertThat(BarcodeFormat.canonicalize(code)).isEmpty();
    }

    @Test
    void rejectsNull() {
        assertThat(BarcodeFormat.canonicalize(null)).isEmpty();
    }
}
