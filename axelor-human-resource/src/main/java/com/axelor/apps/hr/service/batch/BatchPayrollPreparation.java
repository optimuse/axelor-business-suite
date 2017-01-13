package com.axelor.apps.hr.service.batch;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Period;
import com.axelor.apps.base.db.WeeklyPlanning;
import com.axelor.apps.base.db.repo.CompanyRepository;
import com.axelor.apps.base.db.repo.PeriodRepository;
import com.axelor.apps.hr.db.Employee;
import com.axelor.apps.hr.db.HrBatch;
import com.axelor.apps.hr.db.PayrollPreparation;
import com.axelor.apps.hr.db.repo.HrBatchRepository;
import com.axelor.apps.hr.db.repo.PayrollPreparationRepository;
import com.axelor.apps.hr.exception.IExceptionMessage;
import com.axelor.apps.hr.service.PayrollPreparationService;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.beust.jcommander.internal.Lists;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;



public class BatchPayrollPreparation extends BatchStrategy {
	
	private final Logger log = LoggerFactory.getLogger( getClass() );
	
	private int duplicateAnomaly;
	private int configurationAnomaly;
	private int total;
	private HrBatch hrBatch;
	private Company company;
	
	protected PayrollPreparationService payrollPreparationService;
	
	@Inject
	PayrollPreparationRepository payrollPreparationRepository;
	
	@Inject
	CompanyRepository companyRepository;
	
	@Inject
	PeriodRepository periodRepository;
	
	@Inject
	public BatchPayrollPreparation(PayrollPreparationService payrollPreparationService) {
		super();
		this.payrollPreparationService = payrollPreparationService;
	}
	
	@Override
	protected void start() throws IllegalArgumentException, IllegalAccessException, AxelorException {
		
		super.start();
		
		duplicateAnomaly = 0;
		configurationAnomaly = 0;
		total = 0;
		hrBatch = Beans.get(HrBatchRepository.class).find(batch.getHrBatch().getId());
		company = Beans.get(CompanyRepository.class).find(hrBatch.getCompany().getId());
		
		checkPoint();
	}

	
	@Override
	protected void process() {
	
		List<Employee> employeeList = this.getEmployees(hrBatch);
		generatePayrollPreparations(employeeList);
	}
	
	
	public List<Employee> getEmployees(HrBatch hrBatch){
		
		List<String> query = Lists.newArrayList();
		
		if ( !hrBatch.getEmployeeSet().isEmpty() ){
			String employeeIds = Joiner.on(',').join(  
					Iterables.transform(hrBatch.getEmployeeSet(), new Function<Employee,String>() {
			            public String apply(Employee obj) {
			                return obj.getId().toString();
			            }
			        }) ); 
			query.add("self.id IN (" + employeeIds + ")");
		}
		if ( !hrBatch.getPlanningSet().isEmpty() ){
			String planningIds = Joiner.on(',').join(  
					Iterables.transform(hrBatch.getPlanningSet(), new Function<WeeklyPlanning,String>() {
			            public String apply(WeeklyPlanning obj) {
			                return obj.getId().toString();
			            }
			        }) ); 
			
			query.add("self.planning.id IN (" + planningIds + ")");
		}
		
		List<Employee> employeeList = Lists.newArrayList();
		String liaison = query.isEmpty() ? "" : " AND";
		if (hrBatch.getCompany() != null){
			employeeList = JPA.all(Employee.class).filter(Joiner.on(" AND ").join(query) + liaison + " (EXISTS(SELECT u FROM User u WHERE :company MEMBER OF u.companySet AND self = u.employee) OR NOT EXISTS(SELECT u FROM User u WHERE self = u.employee))").bind("company", hrBatch.getCompany()).fetch();
		}
		else{
			employeeList = JPA.all(Employee.class).filter(Joiner.on(" AND ").join(query)).fetch();
		}
		
		return employeeList;
	}
	
	
	public void generatePayrollPreparations(List<Employee> employeeList){
		
		
		for (Employee employee : employeeList) {
			try{
				createPayrollPreparation(employeeRepository.find(employee.getId()) );
			}
			catch(AxelorException e){
				TraceBackService.trace(e, IException.LEAVE_MANAGEMENT, batch.getId());
				incrementAnomaly();
				if (e.getcategory() == IException.NO_UNIQUE_KEY) { duplicateAnomaly ++; }
				else if (e.getcategory() == IException.CONFIGURATION_ERROR) { configurationAnomaly ++; }
			}
			finally {
				total ++;
				JPA.clear();
			}
		}
	}
	
	
	@Transactional
	public void createPayrollPreparation(Employee employee) throws AxelorException{  
		
		List<PayrollPreparation> payrollPreparationList = payrollPreparationRepository.all().filter("self.period = ?1 AND self.employee = ?2 AND self.company = ?3", hrBatch.getPeriod(), employee, company).fetch();
		log.debug("list : " + payrollPreparationList);
		if ( !payrollPreparationList.isEmpty() ){
			throw new AxelorException(String.format(I18n.get(IExceptionMessage.PAYROLL_PREPARATION_DUPLICATE), employee.getName(), hrBatch.getCompany().getName(), hrBatch.getPeriod().getName() ), IException.NO_UNIQUE_KEY );
		}
		Company currentCompany = companyRepository.find(company.getId());
		Period period = periodRepository.find(hrBatch.getPeriod().getId());
		
		PayrollPreparation payrollPreparation = new PayrollPreparation();
		
		payrollPreparation.setCompany( currentCompany );
		payrollPreparation.setEmployee( employee );
		payrollPreparation.setEmploymentContract( employee.getMainEmploymentContract() );
		payrollPreparation.setPeriod( period );
		
		payrollPreparationService.fillInPayrollPreparation(payrollPreparation);
		payrollPreparationRepository.save(payrollPreparation);
		updateEmployee(employee);
	}
	
	@Override
	protected void stop() {
		
		String comment = String.format(I18n.get(IExceptionMessage.BATCH_PAYROLL_PREPARATION_RECAP) + '\n', total); 
		
		comment += String.format(I18n.get(IExceptionMessage.BATCH_PAYROLL_PREPARATION_SUCCESS_RECAP) + '\n', batch.getDone()); 
		
		if (duplicateAnomaly > 0){
			comment += String.format(I18n.get(IExceptionMessage.BATCH_PAYROLL_PREPARATION_DUPLICATE_RECAP) + '\n', duplicateAnomaly); 
		}
		
		if (configurationAnomaly > 0){
			comment += String.format(I18n.get(IExceptionMessage.BATCH_PAYROLL_PREPARATION_CONFIGURATION_RECAP) + '\n', configurationAnomaly); 
		}
		
		addComment(comment);
		super.stop();
	}

}