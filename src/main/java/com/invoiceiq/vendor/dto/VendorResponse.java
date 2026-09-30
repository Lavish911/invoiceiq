package com.invoiceiq.vendor.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class VendorResponse {
    private UUID id;
    private String name;
    private String taxId;
    private String address;
    private String bankDetails;
}
