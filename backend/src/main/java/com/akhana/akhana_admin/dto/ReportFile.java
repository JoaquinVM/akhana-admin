package com.akhana.akhana_admin.dto;

/** An exported report (FIN-10): {@code akhana-<report>-<from>-<to>.xlsx} and its bytes. */
public record ReportFile(String fileName, byte[] content) {
}
