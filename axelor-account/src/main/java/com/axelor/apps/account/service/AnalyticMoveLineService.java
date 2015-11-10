package com.axelor.apps.account.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.axelor.apps.account.db.AnalyticMoveLine;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.repo.AnalyticMoveLineRepository;
import com.axelor.apps.account.service.invoice.generator.InvoiceLineGenerator;
import com.axelor.apps.base.db.IPriceListLine;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.service.administration.GeneralService;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;

public class AnalyticMoveLineService {
	
	@Inject
	protected GeneralService generalService;
	
	@Inject
	protected AnalyticMoveLineRepository analyticMoveLineRepository;
	
	@Inject
	protected AnalyticDistributionLineService analyticDistributionLineService;
	
	public List<InvoiceLine> createInvoiceLines(Invoice invoice, List<AnalyticMoveLine> analyticMoveLineList) throws AxelorException  {

		List<InvoiceLine> invoiceLineList = new ArrayList<InvoiceLine>();

		for(AnalyticMoveLine analyticMoveLine : analyticMoveLineList)  {

			invoiceLineList.addAll(this.createInvoiceLine(invoice, analyticMoveLine));

		}

		return invoiceLineList;

	}

	public List<InvoiceLine> createInvoiceLine(Invoice invoice, AnalyticMoveLine analyticMoveLine) throws AxelorException  {

		Product product = analyticMoveLine.getProduct();
		InvoiceLineGenerator invoiceLineGenerator = new InvoiceLineGenerator(invoice, product, product.getName(), product.getSalePrice().multiply(new BigDecimal(-1)),
					product.getSalePrice().multiply(new BigDecimal(-1)),null,analyticMoveLine.getQte(),product.getUnit(), null,10,BigDecimal.ZERO,IPriceListLine.AMOUNT_TYPE_NONE,
					null, null,false)  {

			@Override
			public List<InvoiceLine> creates() throws AxelorException {

				InvoiceLine invoiceLine = this.createInvoiceLine();

				List<InvoiceLine> invoiceLines = new ArrayList<InvoiceLine>();
				invoiceLines.add(invoiceLine);

				return invoiceLines;
			}
		};

		return invoiceLineGenerator.creates();
	}
}