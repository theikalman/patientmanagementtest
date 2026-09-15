package com.xtramile.patient.web.dto;

import com.xtramile.patient.domain.AustralianState;

/**
 * Outbound address.
 *
 * @param formatted the whole address on one line, so the Angular grid does not have to know
 *                  how Australian addresses are laid out
 */
public record AddressResponse(
        String street,
        String suburb,
        AustralianState state,
        String stateName,
        String postcode,
        String formatted) {
}
