/**
 * Axelor Business Solutions
 *
 * Copyright (C) 2017 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.account.ebics.xml;

import com.axelor.apps.account.ebics.client.EbicsSession;
import com.axelor.apps.account.ebics.client.OrderType;
import com.axelor.apps.account.ebics.client.EbicsUtils;
import com.axelor.exception.AxelorException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.account.ebics.client.UnsecuredRequestElement;

/**
 * The INI request XML element. This root element is to be sent
 * to the ebics server to initiate the signature certificate.
 *
 * @author hachani
 *
 */
public class INIRequestElement extends DefaultEbicsRootElement {
	
	private final Logger log = LoggerFactory.getLogger( getClass() );

  /**
   * Constructs a new INI request element.
   * @param session the ebics session.
   * @param orderId the order id, if null a random one is generated.
   */
  public INIRequestElement(EbicsSession session, String orderId) {
    super(session);
    this.orderId = orderId;
  }

  @Override
  public String getName() {
    return "INIRequest.xml";
  }

  @Override
  public void build() throws AxelorException {
    SignaturePubKeyOrderDataElement		signaturePubKey;
    
    signaturePubKey = new SignaturePubKeyOrderDataElement(session);
    log.debug("SignaturePubKeyOrderDataElement OK");
    signaturePubKey.build();
    log.debug("signaturePubKey.build OK");
	unsecuredRequest = new UnsecuredRequestElement(session,
	                                           OrderType.INI,
	                                           orderId == null ? session.getUser().getEbicsPartner().getNextOrderId() : orderId,
	                                           EbicsUtils.zip(signaturePubKey.prettyPrint()));
	
	log.debug("UnsecuredRequestElement OK");
    unsecuredRequest.build();
    log.debug("unsecuredRequest.build OK");
  }

  @Override
  public byte[] toByteArray() {
    setSaveSuggestedPrefixes("http://www.ebics.org/H003", "");

    return unsecuredRequest.toByteArray();
  }

  @Override
  public void validate() throws AxelorException {
    unsecuredRequest.validate();
  }

  // --------------------------------------------------------------------
  // DATA MEMBERS
  // --------------------------------------------------------------------

  private String			orderId;
  private UnsecuredRequestElement	unsecuredRequest;
}