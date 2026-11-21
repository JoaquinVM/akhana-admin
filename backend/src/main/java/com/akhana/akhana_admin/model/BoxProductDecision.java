package com.akhana.akhana_admin.model;

// COM-10: what was decided about a box sold as its own product in the V1
public enum BoxProductDecision {
    // It became a package of its base product and was deleted
    CONVERTED,
    // It is not a box: it stays a product and is not proposed again
    DISCARDED
}
