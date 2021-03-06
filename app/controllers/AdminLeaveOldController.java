package controllers;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.transaction.Synchronization;

import com.avaje.ebean.Expr;

import action.AdminHRAnnotation;
import action.BasicAuth;
import action.ManagerAnnotation;
import bean.ApplyLeaveBean;
import bean.EntitlementBean;
import bean.HolidaysBean;
import bean.LeaveApprovalByAdminBean;
import bean.LeaveTypeBean;
import bean.WorkingDaysBean;
import controllers.SampleDataController.SMTPAuthenticator;
import models.Alert;
import models.AppUser;
import models.Attendance;
import models.AttendenceStatus;
import models.Role;
import models.lead.NotificationAlert;
import models.leave.AppliedLeaveType;
import models.leave.AppliedLeaves;
import models.leave.DateWiseAppliedLeaves;
import models.leave.Entitlement;
import models.leave.Holidays;
import models.leave.LeaveStatus;
import models.leave.LeaveType;
import models.leave.Leaves;
import models.leave.WorkingDays;
import play.Logger;
import play.data.DynamicForm;
import play.data.Form;
import play.libs.F;
import play.libs.F.Promise;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import utils.Constants;
import utils.EmailService;
import utils.StreamStatusComparator;

public class AdminLeaveOldController extends Controller {

	public static final Form<LeaveTypeBean> leaveTypeform = Form.form(LeaveTypeBean.class);
	public static final Form<WorkingDaysBean> workingDaysform = Form.form(WorkingDaysBean.class);
	public static final Form<HolidaysBean> holidaysBeanform = Form.form(HolidaysBean.class);
	public static final Form<EntitlementBean> entitlementBeanform = Form.form(EntitlementBean.class);
	public static final Form<ApplyLeaveBean> applyLeaveBeanform = Form.form(ApplyLeaveBean.class);
	public static final Form<LeaveApprovalByAdminBean> leaveResponseForm = Form.form(LeaveApprovalByAdminBean.class);

	@AdminHRAnnotation
	public Result getLeaveTypes() {
		return ok(views.html.admin.leave.leaveTypes.render(null));
	}

	@AdminHRAnnotation
	public Result saveLeaveTypes() {
		final Form<LeaveTypeBean> filledForm = leaveTypeform.bindFromRequest();
		if (filledForm.hasErrors()) {
			return redirect(routes.AdminLeaveController.getLeaveTypes());
		} else {
			LeaveTypeBean leaveTypeBean = filledForm.get();
			LeaveType leaveType = leaveTypeBean.toEnLeaveType();
		}

		return redirect(routes.AdminLeaveController.getLeaveTypes());

	}

	@AdminHRAnnotation
	public Result editLeaveType(Long id) {
		LeaveType leaveType = LeaveType.find.byId(id);

		return ok(views.html.admin.leave.leaveTypes.render(leaveType));

	}

	@AdminHRAnnotation
	public Result getWorkingDays() {
		return ok(views.html.admin.leave.addWorkingDays.render());
	}

	@AdminHRAnnotation
	public Result saveWorkingDays() {
		final Form<WorkingDaysBean> filledForm = workingDaysform.bindFromRequest();
		if (filledForm.hasErrors()) {
			return redirect(routes.AdminLeaveController.getWorkingDays());
		} else {
			WorkingDaysBean workBean = filledForm.get();
			WorkingDays workingDays = workBean.toDays();
		}
		return redirect(routes.AdminLeaveController.getWorkingDays());
	}

	@AdminHRAnnotation
	public Result getAddHolidays() {
		return ok(views.html.admin.leave.createHoliday.render(null));

	}

	@AdminHRAnnotation
	public Result saveHoliday() {
		final Form<HolidaysBean> filledForm = holidaysBeanform.bindFromRequest();
		if (filledForm.hasErrors()) {
			return redirect(routes.AdminLeaveController.getAddHolidays());
		} else {
			HolidaysBean holidaysBean = filledForm.get();
			Holidays holidays = holidaysBean.toHolidays();
		}
		return redirect(routes.AdminLeaveController.getAddHolidays());
	}

	@AdminHRAnnotation
	public Result deleteHoliday(Long id) {
		Holidays holidays = Holidays.find.byId(id);
		if (holidays != null) {
			holidays.delete();
		}
		return redirect(routes.AdminLeaveController.getAddHolidays());
	}

	@AdminHRAnnotation
	public Result editHoliday(Long id) {
		Holidays holidays = Holidays.find.byId(id);
		return ok(views.html.admin.leave.createHoliday.render(holidays));

	}

	@AdminHRAnnotation
	public Result getEntitlement() {
		return ok(views.html.admin.leave.createEntitlement.render());

	}

	@AdminHRAnnotation
	public Result saveEntitlement() {
		final Form<EntitlementBean> filledForm = entitlementBeanform.bindFromRequest();
		if (filledForm.hasErrors()) {
			return redirect(routes.AdminLeaveController.getEntitlement());
		} else {
			EntitlementBean entitlementBean = filledForm.get();
			Entitlement entitlement = entitlementBean.toEntitlement();
		}
		return redirect(routes.AdminLeaveController.getEntitlement());
	}

	@BasicAuth
	public Result getConfigureLeaves() {
		// Logger.debug("hiiiii"+Application.getLoggedInUserRole());

		/*
		 * if(Application.getLoggedInUserRole().equals(Roles.Admin) ||
		 * Application.getLoggedInUserRole().equals(Roles.HR)){
		 * //Logger.debug("role...."+Application.getLoggedInUserRole());
		 */
		return ok(views.html.admin.leave.leavesTab.render());
		/*
		 * } return redirect(routes.AdminLeaveController.getApplyLeave());
		 */

	}

	@BasicAuth
	public Result getApplyLeave() {
		return ok(views.html.admin.leave.apply_leave.render());
	}

	@BasicAuth
	public Result getLeaveDetails(Long id) {
		LeaveType leaves = LeaveType.find.byId(id);

		return ok(views.html.admin.leave.applyLeaveDiv.render(leaves));
	}

	@BasicAuth
	public Result getHolidaysList() {
		Date currentDate = new Date();
		@SuppressWarnings("deprecation")
		String lastyear = new SimpleDateFormat("yyyy").format(currentDate);
		// Logger.debug(currentDate.getYear()+"...."+lastyear);
		// int newxtYear = lastyear +2;
		List<String> hoilidaysList = new ArrayList<String>();
		try {
			Date startdate = new SimpleDateFormat("dd-mm-yyyy").parse("01-01-" + lastyear);
			// Date endate = new
			// SimpleDateFormat("dd-mm-yyyy").parse("31-12-"+lastyear);
			Calendar cal = Calendar.getInstance();
			// Calendar cal1 = Calendar.getInstance();
			cal.setTime(startdate);
			cal.add(Calendar.DATE, -1);
			/*
			 * cal1.setTime(endate); cal1.add(Calendar.DATE,-1);
			 */
			List<Holidays> holidays = Holidays.find.where().gt("holidayDate", cal.getTime()).findList();
			// Logger.debug("holidaysList>>>."+holidays);
			for (Holidays holiday : holidays) {
				// Logger.debug("1>>>."+hoilidaysList);
				hoilidaysList.add(new SimpleDateFormat("MM/dd/yyyy").format(holiday.holidayDate));
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// Logger.debug("1>>>."+hoilidaysList);
		return ok(Json.toJson(hoilidaysList));
	}

	@BasicAuth
	public Result applyLeave() {
		final Form<ApplyLeaveBean> filledForm = applyLeaveBeanform.bindFromRequest();
		// Logger.debug("form details>>."+filledForm);
		if (filledForm.hasErrors()) {

			return redirect(routes.AdminLeaveController.getApplyLeave());
		} else {
			// Logger.debug("form details>>."+filledForm);
			ApplyLeaveBean applyLeaveBean = filledForm.get();
			String leaves = applyLeaveBean.toApplyLeavenew();
			flash().put("alert", new Alert("alert-success", leaves).toString());
		}

		return redirect(routes.AdminLeaveController.getApplyLeave());
	}

	public Result getApplyWFH() {
		return ok(views.html.admin.leave.applyWFHDiv.render());
	}

	public Result applyWFH() {

		Attendance attendanceCheck = Attendance.find.where().eq("appUser", Application.getLoggedInUser())
				.eq("date", EngineerController.getTodayDate(new Date())).findUnique();
		if (attendanceCheck == null) {
			Attendance attendance = new Attendance();
			attendance.setDate(EngineerController.getTodayDate(new Date()));
			attendance.setStatus(AttendenceStatus.WFH);
			attendance.setAppUser(Application.getLoggedInUser());
			attendance.save();
			flash().put("alert", new Alert("alert-success", "Today WFH Successfully Applied").toString());
		} else {
			flash().put("alert", new Alert("alert-success", "Already Applied").toString());
		}
		return redirect(routes.AdminLeaveController.getApplyLeave());
	}

	@AdminHRAnnotation
	public Result getAdminLeaveTracker() {
		Date currentDate = new Date();
		@SuppressWarnings("deprecation")
		String lastyear = new SimpleDateFormat("yyyy").format(currentDate);
		List<AppliedLeaves> appliedLeavesList = new ArrayList<>();
		try {
			Date startdate = new SimpleDateFormat("dd-mm-yyyy").parse("01-01-" + lastyear);
			Date endate = new SimpleDateFormat("dd-mm-yyyy").parse("31-12-" + lastyear);
			Calendar cal = Calendar.getInstance();
			Calendar cal1 = Calendar.getInstance();
			cal.setTime(startdate);
			cal.add(Calendar.DATE, -1);
			cal1.setTime(endate);
			cal1.add(Calendar.DATE, 1);
			appliedLeavesList.addAll(AppliedLeaves.find.where().gt("startDate", cal.getTime())
					.ne("appUser", Application.getLoggedInUser()).order("id desc").findList());

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// Collections.reverse(appliedLeavesList);
		return ok(views.html.admin.leave.adminLeaveTracker.render(appliedLeavesList,LeaveStatus.PENDING_APPROVAL));
	}

	public Result displayAppliedLeaveDetails(Long id) {
		AppliedLeaves appliedLeaves = AppliedLeaves.find.byId(id);
		return ok(views.html.admin.leave.leaveTrackerEmployeDiv.render(appliedLeaves));

	}

	public Result approvalByAdmin() {
		final Form<LeaveApprovalByAdminBean> filledForm = leaveResponseForm.bindFromRequest();
		if (filledForm.hasErrors()) {

			return redirect(routes.AdminLeaveController.getAdminLeaveTracker());
		} else {
			LeaveApprovalByAdminBean leaveBean = filledForm.get();
			String message = leaveBean.leaveApproval();
			flash().put("alert", new Alert("alert-success", message).toString());
			// Logger.debug("sasasas>>"+Application.getLoggedInUserRole());
			if (Application.getLoggedInUserRole().equals("Manager")) {
				return redirect(routes.AdminLeaveController.getTeamLeaveTracker());
			}
		}
		return redirect(routes.AdminLeaveController.getAdminLeaveTracker());
	}

	@BasicAuth
	public Result getLeaveStatus() {
		List<AppliedLeaves> appliedLeavesList = new ArrayList<>();
		for (LeaveType leaveType : LeaveType.find.all()) {
			appliedLeavesList.addAll(AppliedLeaves.appliedLeavesList(Application.getLoggedInUser(), leaveType));
			appliedLeavesList.addAll(AppliedLeaves.approvedLeavesList(Application.getLoggedInUser(), leaveType));
			appliedLeavesList.addAll(AppliedLeaves.takenLeavesList(Application.getLoggedInUser(), leaveType));
			appliedLeavesList.addAll(AppliedLeaves.cacelledLeavesList(Application.getLoggedInUser(), leaveType));
			appliedLeavesList.addAll(AppliedLeaves.rejectedLeavesList(Application.getLoggedInUser(), leaveType));

		}
		// Collections.reverse(appliedLeavesList);
		Collections.sort(appliedLeavesList, new StreamStatusComparator());
		// Logger.debug("ssss>>"+appliedLeavesList);
		return ok(views.html.admin.leave.leave_Status.render(appliedLeavesList));
	}

	@BasicAuth
	public synchronized Result cancelLeave(Long appLiedLeaveId) {
		AppliedLeaves appliedLeaves = AppliedLeaves.find.byId(appLiedLeaveId);
		String content = "This mail is to intimate you that" + " " + Application.getLoggedInUser().getAppUserFullName()
				+ " " + "has cancelled his leave for the below dates \n\n Start date:- "
				+ new SimpleDateFormat("dd-MM-yyy").format(appliedLeaves.startDate) + "\n\n End date:- "
				+ new SimpleDateFormat("dd-MM-yyy").format(appliedLeaves.endDate);
		if (appliedLeaves != null && appliedLeaves.leaveStatus.equals(LeaveStatus.PENDING_APPROVAL)) {
			appliedLeaves.leaveStatus = LeaveStatus.CANCELLED;
			appliedLeaves.update();
			Date currentDate = new Date();
			@SuppressWarnings("deprecation")
			String lastyear = new SimpleDateFormat("yyyy").format(currentDate);
			try {
				Date startdate = new SimpleDateFormat("yyyy").parse(lastyear);

				Leaves leaves = Leaves.find.where().eq("appUser", Application.getLoggedInUser())
						.eq("leaveType", appliedLeaves.leaveType).eq("year", startdate).findUnique();
				if (leaves != null) {
					leaves.usedLeaves -= appliedLeaves.totalLeaves;
					leaves.remainingLeaves += appliedLeaves.totalLeaves;
					leaves.update();
				}
				final Promise<Boolean> emailResult = Promise.promise(new F.Function0<Boolean>() {
					@Override
					public Boolean apply() throws Throwable {
						// cancelledNitifications(appliedLeaves);

						return cancelledNitifications(appliedLeaves);
					}
				});
				final Promise<Boolean> emailResult1 = Promise.promise(new F.Function0<Boolean>() {
					@Override
					public Boolean apply() throws Throwable {
						// cancelledNitifications(appliedLeaves);

						return leaveCancelEmail(appliedLeaves);
					}
				});
				// EmailService.sendVerificationMail(models.AppUser.getReptManager(Application.getLoggedInUser().reportMangerId).getEmail(),
				// content, "Cancelled Leave !!");
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		flash().put("alert", new Alert("alert-success", "Cancelled Successfully").toString());
		return redirect(routes.AdminLeaveController.getLeaveStatus());
	}

	@BasicAuth
	public Result cancelLeaveApproved() {
		DynamicForm form = Form.form().bindFromRequest();
		String appLiedLeaveId = form.get("appLiedLeaveId");
		// Logger.debug(appLiedLeaveId+"........");
		AppliedLeaves appliedLeaves = AppliedLeaves.find.byId(Long.parseLong(appLiedLeaveId));
		// String content ="This mail is to intimate you that"+"
		// "+Application.getLoggedInUser().getAppUserFullName()+" "+"has
		// cancelled his leave for the below dates \n\n Start date:- "+new
		// SimpleDateFormat("dd-MM-yyy").format(appliedLeaves.startDate)+"\n\n
		// End date:- "+new
		// SimpleDateFormat("dd-MM-yyy").format(appliedLeaves.endDate);
		if (appliedLeaves != null) {
			appliedLeaves.leaveStatus = LeaveStatus.CANCELLED;
			appliedLeaves.update();
			Date currentDate = new Date();
			@SuppressWarnings("deprecation")
			String lastyear = new SimpleDateFormat("yyyy").format(currentDate);
			try {
				Date startdate = new SimpleDateFormat("yyyy").parse(lastyear);

				Leaves leaves = Leaves.find.where().eq("appUser", appliedLeaves.appUser)
						.eq("leaveType", appliedLeaves.leaveType).eq("year", startdate).findUnique();
				if (leaves != null) {
					leaves.usedLeaves -= appliedLeaves.totalLeaves;
					leaves.remainingLeaves += appliedLeaves.totalLeaves;
					leaves.update();
				}
				final Promise<Boolean> emailResult = Promise.promise(new F.Function0<Boolean>() {
					@Override
					public Boolean apply() throws Throwable {
						// cancelledNitifications(appliedLeaves);

						return approvedLeaveCancelEmail(appliedLeaves);
					}
				});

				/*
				 * if(Application.getLoggedInUser().reportMangerId >0){
				 * EmailService.sendVerificationMail(models.AppUser.
				 * getReptManager(Application.getLoggedInUser().reportMangerId).
				 * getEmail(), content, "Cancelled Leave !!"); }
				 */
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		flash().put("alert", new Alert("alert-success", "Cancelled Successfully").toString());
		if (Application.getLoggedInUserRole().equals("Admin") || Application.getLoggedInUserRole().equals("HR")) {
			return redirect(routes.AdminLeaveController.getAdminLeaveTracker());
		}
		return redirect(routes.AdminLeaveController.getTeamLeaveTracker());
	}

	@ManagerAnnotation
	public Result getTeamLeaveTracker() {
		Date currentDate = new Date();
		@SuppressWarnings("deprecation")
		String lastyear = new SimpleDateFormat("yyyy").format(currentDate);
		List<AppliedLeaves> appliedLeavesList = new ArrayList<>();
		try {
			Date startdate = new SimpleDateFormat("dd-mm-yyyy").parse("01-01-" + lastyear);
			// Date endate = new
			// SimpleDateFormat("dd-mm-yyyy").parse("31-12-"+lastyear);
			Calendar cal = Calendar.getInstance();
			// Calendar cal1 = Calendar.getInstance();
			cal.setTime(startdate);
			cal.add(Calendar.DATE, -1);
			/*
			 * cal1.setTime(endate); cal1.add(Calendar.DATE,-1);
			 */

			for (AppUser appUser : AppUser.find.where().eq("reportMangerId", Application.getLoggedInUser().id)
					.findList()) {
				appliedLeavesList.addAll(AppliedLeaves.find.where().eq("appUser", appUser)
						.gt("startDate", cal.getTime()).order("id desc").findList());
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// Collections.reverse(appliedLeavesList);
		return ok(views.html.admin.leave.teamLeaveTracker.render(appliedLeavesList));
	}

	public Result getDefaultCalendar() {
		final Calendar cal = Calendar.getInstance();
		final Integer month = cal.get(Calendar.MONTH);
		final Integer year = cal.get(Calendar.YEAR);
		return redirect(routes.AdminLeaveController.getLeaveCalender(month, year));
	}

	public Result getLeaveCalender(Integer month, Integer year) {

		final List<Date> dates = new ArrayList<Date>();
		Map<AppUser, String> todaysLeaves = new LinkedHashMap<AppUser, String>();
		List<AppUser> todaysLeavesApproved = new ArrayList<AppUser>();
		List<AppUser> todaysLeavesPendingApproval = new ArrayList<AppUser>();
		List<AppUser> todaysLeavesWFH = new ArrayList<AppUser>();
		List<AppUser> absentUserstoday = new ArrayList<AppUser>();
		final Map<Date, Integer> leavesMap = new LinkedHashMap<Date, Integer>();
		final Map<Date, List<AppUser>> currentMonthLeaves = new LinkedHashMap<Date, List<AppUser>>();
		final Map<Date, List<AppUser>> nextMonthLeaves = new LinkedHashMap<Date, List<AppUser>>();
		final Map<Date, Holidays> holidaysMap = new LinkedHashMap<Date, Holidays>();
		final Map<Integer, List<Date>> dayDateMap = new LinkedHashMap<Integer, List<Date>>();
		final Map<Date, Map<String, List<AppUser>>> leavesTotal = new LinkedHashMap<Date, Map<String, List<AppUser>>>();
		final Map<Date, Map<String, List<AppUser>>> leavesTotalNextMonth = new LinkedHashMap<Date, Map<String, List<AppUser>>>();

		for (int i = 1; i < 8; i++) {
			dayDateMap.put(i, new ArrayList<Date>());
		}

		Date currentDate = new Date();
		final Calendar cal = Calendar.getInstance();

		try {
			currentDate = new SimpleDateFormat("dd-MM-yyyy")
					.parse(new SimpleDateFormat("dd-MM-yyyy").format(cal.getTime()));
		} catch (Exception e) {
			e.printStackTrace();
		}

		cal.set(Calendar.DAY_OF_MONTH, 1);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		cal.set(Calendar.MONTH, month);
		cal.set(Calendar.YEAR, year);

		final int myMonth = cal.get(Calendar.MONTH);
		final int myYear = cal.get(Calendar.YEAR);
		final Date firstDate = cal.getTime();

		final Integer firstDayOfMonth = cal.get(Calendar.DAY_OF_WEEK);

		/** for adding nulls in leading array **/
		final Integer leadingDays = 7 - (8 - firstDayOfMonth);
		for (int i = 0; i < leadingDays; i++) {
			dates.add(null);
		}
		final Integer currentMonth = cal.get(Calendar.MONTH);
		Integer lastDayOfMonth = null;
		Date lastDate = null;

		// current month
		while (myMonth == cal.get(Calendar.MONTH)) {

			dayDateMap.get(cal.get(Calendar.DAY_OF_WEEK)).add(cal.getTime());
			final Date today = cal.getTime();
			dates.add(today);
			lastDayOfMonth = cal.get(Calendar.DAY_OF_WEEK);
			/**
			 * get the count of events on that day
			 */
			cal.add(Calendar.DATE, 1);
			final Date tomorrow = cal.getTime();
			lastDate = cal.getTime();
			Integer count = 0;
			List<DateWiseAppliedLeaves> dateList = new ArrayList<>();
			List<AppUser> userOnLeave = new ArrayList<>();
			List<AppUser> userOnLeaveApplied = new ArrayList<>();
			List<AppUser> absentUsers = new ArrayList<AppUser>();
			List<AppUser> WFHUsers = new ArrayList<AppUser>();
			final Map<String, List<AppUser>> totalLeaveUsers = new LinkedHashMap<String, List<AppUser>>();

			// current month - Approved and Pending Leaves
			dateList.addAll(DateWiseAppliedLeaves.find.where().eq("leaveDate", today).findList());
			for (DateWiseAppliedLeaves dateleave : dateList) {
				AppliedLeaves appliedLeaves = AppliedLeaves.find.where().in("dateLeaves", dateleave)
						.or(com.avaje.ebean.Expr.eq("leaveStatus", LeaveStatus.APPROVED),
								com.avaje.ebean.Expr.eq("leaveStatus", LeaveStatus.TAKEN))
						.findUnique();
				AppliedLeaves appliedLeavesnew = AppliedLeaves.find.where().in("dateLeaves", dateleave)
						.eq("leaveStatus", LeaveStatus.PENDING_APPROVAL).findUnique();

				if (appliedLeaves != null) {
					count++;
					userOnLeave.add(appliedLeaves.appUser);

					if (today.equals(currentDate)) {

						String period = new SimpleDateFormat("dd-MMM-yyyy").format(appliedLeaves.startDate) + " to "
								+ new SimpleDateFormat("dd-MMM-yyyy").format(appliedLeaves.endDate);
						todaysLeaves.put(appliedLeaves.appUser, period);
						if (appliedLeaves.leaveStatus.equals(LeaveStatus.APPROVED)) {
							todaysLeavesApproved.add(appliedLeaves.appUser);
						}

						if (appliedLeaves.leaveStatus.equals(LeaveStatus.PENDING_APPROVAL)) {
							todaysLeavesPendingApproval.add(appliedLeaves.appUser);
						}
					}

				}

				if (appliedLeavesnew != null) {
					count++;
					userOnLeaveApplied.add(appliedLeavesnew.appUser);
					if (today.equals(currentDate)) {
						String period1 = new SimpleDateFormat("dd-MMM-yyyy").format(appliedLeavesnew.startDate) + " to "
								+ new SimpleDateFormat("dd-MMM-yyyy").format(appliedLeavesnew.endDate);
						todaysLeaves.put(appliedLeavesnew.appUser, period1);
						if (appliedLeavesnew.leaveStatus.equals(LeaveStatus.APPROVED)) {
							todaysLeavesApproved.add(appliedLeavesnew.appUser);
						}

						if (appliedLeavesnew.leaveStatus.equals(LeaveStatus.PENDING_APPROVAL)) {
							todaysLeavesPendingApproval.add(appliedLeavesnew.appUser);
						}
					}
				}
			}

			if (!userOnLeaveApplied.isEmpty()) {
				totalLeaveUsers.put("p", userOnLeaveApplied);
			}
			if (!userOnLeave.isEmpty()) {
				totalLeaveUsers.put("a", userOnLeave);
			}

			// current month - today - Absent List
			List<Attendance> attendanceList = Attendance.find.where().eq("date", today)
					.eq("status", AttendenceStatus.Absent).findList();
			for (Attendance attendance : attendanceList) {

				if (!userOnLeave.contains(attendance.appUser) && !userOnLeaveApplied.contains(attendance.appUser)) {
					count++;
					absentUsers.add(attendance.appUser);
					if (today.equals(currentDate)) {
						absentUserstoday.add(attendance.appUser);
						String period = new SimpleDateFormat("dd-MMM-yyyy").format(attendance.date) + " to "
								+ new SimpleDateFormat("dd-MMM-yyyy").format(attendance.date);
						todaysLeaves.put(attendance.appUser, period);
					}
				}
			}

			if (!absentUsers.isEmpty()) {
				totalLeaveUsers.put("na", absentUsers);
			}
			if (!totalLeaveUsers.isEmpty()) {
				leavesTotal.put(today, totalLeaveUsers);
			}

			// current month - today - Work from Home List
			List<Attendance> attendanceWFHList = Attendance.find.where().eq("date", today)
					.eq("status", AttendenceStatus.WFH).findList();
			for (Attendance attendance : attendanceWFHList) {
				// count++;
				WFHUsers.add(attendance.getAppUser());
				if (today.equals(currentDate)) {
					String period = new SimpleDateFormat("dd-MMM-yyyy").format(attendance.date) + " to "
							+ new SimpleDateFormat("dd-MMM-yyyy").format(attendance.date);
					todaysLeaves.put(attendance.getAppUser(), period);
					todaysLeavesWFH.add(attendance.getAppUser());
				}
			}

			if (!WFHUsers.isEmpty()) {
				totalLeaveUsers.put("wfh", WFHUsers);
			}
			if (!totalLeaveUsers.isEmpty()) {
				leavesTotal.put(today, totalLeaveUsers);
			}

			leavesMap.put(today, count);

			if (!userOnLeave.isEmpty()) {
				currentMonthLeaves.put(today, userOnLeave);
			}
			Holidays holiday = Holidays.find.where().eq("holidayDate", today).findUnique();
			if (holiday != null) {
				holidaysMap.put(today, holiday);
			}
		} // end while loop for current month

		final Integer trailingDays = 7 - lastDayOfMonth;
		/** for adding nulls in trailing array **/
		for (int i = 0; i < trailingDays; i++) {
			dates.add(null);
		}
		final Calendar cal1 = Calendar.getInstance();
		cal1.set(Calendar.DAY_OF_MONTH, 1);
		cal1.set(Calendar.HOUR_OF_DAY, 0);
		cal1.set(Calendar.MINUTE, 0);
		cal1.set(Calendar.SECOND, 0);
		cal1.set(Calendar.MILLISECOND, 0);
		cal1.set(Calendar.MONTH, myMonth + 1);
		cal1.set(Calendar.YEAR, myYear);

		final Date firstDayOfNextMonth = cal1.getTime();

		// next month
		while (myMonth + 1 == cal1.get(Calendar.MONTH)) {
			final Date nexttoday = cal1.getTime();
			Integer count = 0;
			List<DateWiseAppliedLeaves> dateList = new ArrayList<>();
			List<AppUser> userOnLeave = new ArrayList<>();
			List<AppUser> userOnLeaveApplied = new ArrayList<>();
			List<AppUser> absentUsers = new ArrayList<AppUser>();
			List<AppUser> WFHUsers = new ArrayList<AppUser>();
			final Map<String, List<AppUser>> totalLeaveUsers = new LinkedHashMap<String, List<AppUser>>();

			// Next month - Approved and Pending Leaves
			dateList.addAll(DateWiseAppliedLeaves.find.where().eq("leaveDate", nexttoday).findList());
			for (DateWiseAppliedLeaves dateleave : dateList) {
				AppliedLeaves appliedLeaves = AppliedLeaves.find.where().in("dateLeaves", dateleave)
						.or(com.avaje.ebean.Expr.eq("leaveStatus", LeaveStatus.APPROVED),
								com.avaje.ebean.Expr.eq("leaveStatus", LeaveStatus.TAKEN))
						.findUnique();
				AppliedLeaves appliedLeavesnew = AppliedLeaves.find.where().in("dateLeaves", dateleave)
						.eq("leaveStatus", LeaveStatus.PENDING_APPROVAL).findUnique();
				if (appliedLeaves != null) {
					count++;
					userOnLeave.add(appliedLeaves.appUser);
				}
				if (appliedLeavesnew != null) {
					count++;
					userOnLeaveApplied.add(appliedLeavesnew.appUser);
				}
			}

			if (!userOnLeaveApplied.isEmpty()) {
				totalLeaveUsers.put("p", userOnLeaveApplied);
			}
			if (!userOnLeave.isEmpty()) {
				totalLeaveUsers.put("a", userOnLeave);
			}

			// Next month - today - Absent List
			List<Attendance> attendanceList = Attendance.find.where().eq("date", nexttoday)
					.eq("status", AttendenceStatus.Absent).findList();
			for (Attendance attendance : attendanceList) {

				if (!userOnLeave.contains(attendance.appUser) && !userOnLeaveApplied.contains(attendance.appUser)) {

					absentUsers.add(attendance.appUser);
				}
			}

			if (!absentUsers.isEmpty()) {
				totalLeaveUsers.put("na", absentUsers);
			}
			if (!totalLeaveUsers.isEmpty()) {
				leavesTotalNextMonth.put(nexttoday, totalLeaveUsers);
			}

			// Next month - today - WFH List
			List<Attendance> attendanceWFHList = Attendance.find.where().eq("date", nexttoday)
					.eq("status", AttendenceStatus.WFH).findList();
			for (Attendance attendance : attendanceWFHList) {
				WFHUsers.add(attendance.getAppUser());
			}

			if (!WFHUsers.isEmpty()) {
				totalLeaveUsers.put("wfh", WFHUsers);
			}
			if (!totalLeaveUsers.isEmpty()) {
				leavesTotalNextMonth.put(nexttoday, totalLeaveUsers);
			}

			if (!userOnLeave.isEmpty()) {
				nextMonthLeaves.put(nexttoday, userOnLeave);
			}
			cal1.add(Calendar.DATE, 1);

		} // end While loop next month

		Map<Date, List<AppUser>> map = new TreeMap<Date, List<AppUser>>(new Comparator<Date>() {
			public int compare(Date date1, Date date2) {
				return date2.compareTo(date1);
			}
		});
		map.putAll(currentMonthLeaves);

		return ok(views.html.admin.leave.leave_calendar_admin.render(dates, firstDate, myMonth, myYear, leavesMap,holidaysMap, currentMonthLeaves, nextMonthLeaves, leavesTotal, leavesTotalNextMonth, todaysLeaves,todaysLeavesApproved, todaysLeavesPendingApproval, absentUserstoday, todaysLeavesWFH));
	}

	public Result getNextCalendar(Integer selectedMonth, Integer selectedYear) {
		final Calendar cal = Calendar.getInstance();
		cal.set(Calendar.DAY_OF_MONTH, 1);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MONTH, selectedMonth + 1);
		cal.set(Calendar.YEAR, selectedYear);

		final Integer month = cal.get(Calendar.MONTH);
		final Integer year = cal.get(Calendar.YEAR);
		return redirect(routes.AdminLeaveController.getLeaveCalender(month, year));
	}

	public Result getPreviousCalendar(Integer selectedMonth, Integer selectedYear) {

		final Calendar cal = Calendar.getInstance();
		cal.set(Calendar.DAY_OF_MONTH, 1);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MONTH, selectedMonth - 1);
		cal.set(Calendar.YEAR, selectedYear);

		final Integer month = cal.get(Calendar.MONTH);
		final Integer year = cal.get(Calendar.YEAR);
		return redirect(routes.AdminLeaveController.getLeaveCalender(month, year));
	}

	public Result getAppUserEntitlementPage() {
		return ok(views.html.admin.leave.appUserEntitlement.render());
	}

	public Result eachUserEntitlement(Long id) {
		AppUser appUser = AppUser.find.byId(id);
		return ok(views.html.admin.leave.appUserEntitlementDiv.render(appUser));
	}

	public static boolean sendNotification(final AppUser notifiedTo, final AppUser notifiedBy, final String message,
			final String url, final Role role) {/// here only
		boolean result = true;
		try {
			final NotificationAlert notification = new NotificationAlert();
			// Logger.debug("notifiedTo>>>>>"+notifiedTo);
			notification.notifiedBy = notifiedBy;
			notification.notifiedTo = notifiedTo;
			notification.url = url;
			notification.notification = message;
			notification.role = role;
			notification.notificationDate = new Date();
			notification.save();

		} catch (final Exception e) {
			e.printStackTrace();
			result = false;
		}
		return result;
	}

	@BasicAuth
	public Result notificationRedirection(Long notId) {
		NotificationAlert notificationAlert = NotificationAlert.find.byId(notId);
		notificationAlert.alert = true;
		notificationAlert.update();
		return redirect(notificationAlert.url);

	}

	public static boolean sendNotification1(final AppUser notifiedTo, final AppUser notifiedBy, final String message,
			final String url) {/// here only
		boolean result = true;
		try {
			final NotificationAlert notification = new NotificationAlert();
			notification.notifiedBy = notifiedBy;
			notification.notifiedTo = notifiedTo;
			notification.url = url;
			notification.notification = message;
			notification.notificationDate = new Date();
			notification.save();

		} catch (final Exception e) {
			e.printStackTrace();
			result = false;
		}
		return result;
	}

	public Boolean cancelledNitifications(AppliedLeaves appliedLeaves) {
		List<AppUser> appUsersList = new ArrayList<AppUser>();
		List<Role> rolesList = new ArrayList<>();
		// Logger.debug("inside method call");
		Role roleAdmin = Role.find.where().eq("role", "Admin").findUnique();

		rolesList.add(roleAdmin);
		Role roleHr = Role.find.where().eq("role", "HR").findUnique();
		Role roleRepManger = Role.find.where().eq("role", "Manager").findUnique();
		rolesList.add(roleHr);
		appUsersList.addAll(AppUser.find.where().in("role", rolesList).findList());
		if (!appUsersList.contains(appliedLeaves.appUser.getReptManager(appliedLeaves.appUser.getReportMangerId()))) {
			appUsersList.add(appliedLeaves.appUser.getReptManager(appliedLeaves.appUser.getReportMangerId()));
		}
		for (AppUser appUser : appUsersList) {
			if (!Application.getLoggedInUser().equals(appUser)) {
				String rejMessage1 = appliedLeaves.appUser.getAppUserFullName() + " " + "has cancelled his leave from "
						+ new SimpleDateFormat("dd-MM-yyy").format(appliedLeaves.startDate) + " to "
						+ new SimpleDateFormat("dd-MM-yyy").format(appliedLeaves.endDate) + ".";
				if (appUser.role.contains(roleAdmin)) {
					if (appliedLeaves.appUser.getReptManager(appliedLeaves.appUser.getReportMangerId()) != null) {
						if (!appUser.equals(
								appliedLeaves.appUser.getReptManager(appliedLeaves.appUser.getReportMangerId()))) {
							AdminLeaveController.sendNotification(appUser, Application.getLoggedInUser(), rejMessage1,
									"/leave-tracker", roleAdmin);
						} else {
							AdminLeaveController.sendNotification(appUser, Application.getLoggedInUser(), rejMessage1,
									"/team/leave-tracker", roleRepManger);
						}
					} else {
						AdminLeaveController.sendNotification(appUser, Application.getLoggedInUser(), rejMessage1,
								"/leave-tracker", roleAdmin);
					}
				} else if (appUser.role.contains(roleHr)) {
					if (appliedLeaves.appUser.getReptManager(appliedLeaves.appUser.getReportMangerId()) != null) {
						if (!appUser.equals(
								appliedLeaves.appUser.getReptManager(appliedLeaves.appUser.getReportMangerId()))) {
							AdminLeaveController.sendNotification(appUser, Application.getLoggedInUser(), rejMessage1,
									"/leave-tracker", roleHr);
						} else {
							AdminLeaveController.sendNotification(appUser, Application.getLoggedInUser(), rejMessage1,
									"/team/leave-tracker", roleRepManger);
						}
					} else {
						AdminLeaveController.sendNotification(appUser, Application.getLoggedInUser(), rejMessage1,
								"/leave-tracker", roleHr);
					}
				} else {
					AdminLeaveController.sendNotification(appUser, Application.getLoggedInUser(), rejMessage1,
							"/team/leave-tracker", roleRepManger);
				}
				/*
				 * if(appUser.role.contains(roleAdmin) &&
				 * !appUser.equals(appliedLeaves.appUser.getReptManager(
				 * appliedLeaves.appUser.getReportMangerId()))){
				 * AdminLeaveController.sendNotification(appUser,
				 * Application.getLoggedInUser(), rejMessage1,
				 * "/leave-tracker",roleAdmin); }else
				 * if(appUser.role.contains(roleHr) &&
				 * !appUser.equals(appliedLeaves.appUser.getReptManager(
				 * appliedLeaves.appUser.getReportMangerId()))){
				 * AdminLeaveController.sendNotification(appUser,
				 * Application.getLoggedInUser(), rejMessage1,
				 * "/leave-tracker",roleHr); }else{
				 * AdminLeaveController.sendNotification(appUser,
				 * Application.getLoggedInUser(), rejMessage1,
				 * "/team/leave-tracker" ,roleRepManger); }
				 */
			}
		}
		return true;
	}

	public Boolean approvedLeaveCancelEmail(AppliedLeaves appliedLeaves) {
		AppUser appUser = Application.getLoggedInUser();
		List<AppUser> appUsersList = new ArrayList<AppUser>();
		List<Role> rolesList = new ArrayList<>();
		// Logger.debug("inside method call");
		Role roleAdmin = Role.find.where().eq("role", "Admin").findUnique();

		rolesList.add(roleAdmin);
		Role roleHr = Role.find.where().eq("role", "HR").findUnique();
		/*
		 * Role roleRepManger = Role.find.where().eq("role",
		 * "Manager").findUnique(); rolesList.add(roleHr);
		 */
		rolesList.add(roleHr);
		appUsersList.addAll(AppUser.find.where().in("role", rolesList).findList());
		if (appliedLeaves.appUser.getReportMangerId() > 0 && !appUsersList
				.contains(appliedLeaves.appUser.getReptManager(appliedLeaves.appUser.getReportMangerId()))) {
			appUsersList.add(appliedLeaves.appUser.getReptManager(appliedLeaves.appUser.getReportMangerId()));
		}
		String content = "this mail is to intimate you that" + " " + appUser.getAppUserFullName()
				+ " has cancelled below approved leave: \n\n Employee: " + appliedLeaves.appUser.getAppUserFullName()
				+ " \n\n <> Start date:-" + new SimpleDateFormat("dd-MM-yyy").format(appliedLeaves.startDate)
				+ "\n\n End date:- " + new SimpleDateFormat("dd-MM-yyy").format(appliedLeaves.endDate)
				+ " \n\n No. of Days : " + appliedLeaves.totalLeaves;
		String userContent = "Your approved leaves for the below dates has been cancelled by "
				+ appUser.getAppUserFullName() + "\n\n Start date:- "
				+ new SimpleDateFormat("dd-MM-yyy").format(appliedLeaves.startDate) + "\n\n End date:- "
				+ new SimpleDateFormat("dd-MM-yyy").format(appliedLeaves.endDate);
		for (AppUser appUser2 : appUsersList) {
			if (!appUser.equals(appUser2) && !appliedLeaves.appUser.equals(appUser2)) {
				EmailService.sendVerificationMail(appUser2.getEmail(), content, "Approved leave Cancelled !!");
			}
		}
		EmailService.sendVerificationMail(appliedLeaves.appUser.getEmail(), userContent, "Approved leave Cancelled !!");

		return true;

	}

	public Boolean leaveCancelEmail(AppliedLeaves appliedLeaves) {
		AppUser appUser = Application.getLoggedInUser();
		List<AppUser> appUsersList = new ArrayList<AppUser>();
		List<Role> rolesList = new ArrayList<>();
		// Logger.debug("inside method call");
		Role roleAdmin = Role.find.where().eq("role", "Admin").findUnique();

		rolesList.add(roleAdmin);
		Role roleHr = Role.find.where().eq("role", "HR").findUnique();
		/*
		 * Role roleRepManger = Role.find.where().eq("role",
		 * "Manager").findUnique(); rolesList.add(roleHr);
		 */
		rolesList.add(roleHr);
		appUsersList.addAll(AppUser.find.where().in("role", rolesList).findList());
		if (appliedLeaves.appUser.getReportMangerId() > 0 && !appUsersList
				.contains(appliedLeaves.appUser.getReptManager(appliedLeaves.appUser.getReportMangerId()))) {
			appUsersList.add(appliedLeaves.appUser.getReptManager(appliedLeaves.appUser.getReportMangerId()));
		}
		String content = "This mail is to intimate you that" + " " + Application.getLoggedInUser().getAppUserFullName()
				+ " " + "has cancelled his leave for the below dates \n\n Start date:- "
				+ new SimpleDateFormat("dd-MM-yyy").format(appliedLeaves.startDate) + "\n\n End date:- "
				+ new SimpleDateFormat("dd-MM-yyy").format(appliedLeaves.endDate);
		// String userContent = "Your approved leaves for the below dates has
		// been cancelled by "+ appUser.getAppUserFullName()+ "\n\n Start date:-
		// "+new
		// SimpleDateFormat("dd-MM-yyy").format(appliedLeaves.startDate)+"\n\n
		// End date:- "+new
		// SimpleDateFormat("dd-MM-yyy").format(appliedLeaves.endDate);
		for (AppUser appUser2 : appUsersList) {
			if (!appUser.equals(appUser2)) {
				EmailService.sendVerificationMail(appUser2.getEmail(), content, "Cancelled Leave !!");
			}
		}
		// EmailService.sendVerificationMail(appliedLeaves.appUser.getEmail(),
		// userContent, "Cancelled Leave !!");

		return true;

	}

	public static Boolean checkUserExp() {
		Boolean flag = false;
		// Calendar startCalendar = Calendar.getInstance();
		// startCalendar.setTime(Application.getLoggedInUser().getJoinedDate());

		Calendar endCalendar = Calendar.getInstance();

		// int diffYear = endCalendar.get(Calendar.YEAR) -
		// startCalendar.get(Calendar.YEAR);
		// int diffMonth = diffYear * 12 + endCalendar.get(Calendar.MONTH) -
		// startCalendar.get(Calendar.MONTH);

		Date startDate = Application.getLoggedInUser().getJoinedDate();
		Date endDate = endCalendar.getTime();

		int numOfDays = endCalendar.getActualMaximum(Calendar.DAY_OF_YEAR);
		long diff = endDate.getTime() - startDate.getTime();

		Long ttdays = diff / (1000 * 60 * 60 * 24);

		if (numOfDays <= ttdays) {
			flag = true;
		}
		// Logger.debug(" days "+numOfDays + "hlkh"+ ttdays);
		return flag;
	}

	
	public Result cal(){
			
			List<DateWiseAppliedLeaves> listds = DateWiseAppliedLeaves.find.all();
			for(DateWiseAppliedLeaves dateLeaves : listds){
				SimpleDateFormat lastSF = new SimpleDateFormat("dd-MM-yyyy");
				
				try {
					//Date lastDate = lastSF.parse("20-10-2016");
					Date createdOn  = lastSF.parse(lastSF.format(dateLeaves.createdOn));
					Date leaveDate  = lastSF.parse(lastSF.format(dateLeaves.leaveDate)); 
					if(leaveDate.before(createdOn)){
						dateLeaves.appliedLeaveType = AppliedLeaveType.Unplanned;
					}/*else if(createdOn.before(lastDate)){
						dateLeaves.appliedLeaveType = AppliedLeaveType.Planned;
					}*/
					dateLeaves.update();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
				
			}
			
			return ok("Done");
		}
	
	
//	public synchronized Result cal1(){
//		
//		List<Leaves> listds = Leaves.find.all();
//		StringBuffer appAdded = new StringBuffer();
//		for(Leaves leaves : listds){
//			AppUser appUser = leaves.appUser;
//			
//			List<AppliedLeaves> appliedLeaves = AppliedLeaves.find.where().eq("leaveType", leaves.leaveType).eq("appUser", leaves.appUser).or(Expr.eq("leaveStatus",LeaveStatus.APPROVED),Expr.eq("leaveStatus",LeaveStatus.PENDING_APPROVAL)).findList();
//			Float appliLeaves = 0.0f;
//			for(AppliedLeaves appliedLeaves2 : appliedLeaves){
//				appliLeaves += appliedLeaves2.totalLeaves;
//			}
//			if(leaves.remainingLeaves != (leaves.addedLeaves - appliLeaves)){
//				appAdded.append(leaves.appUser.getAppUserFullName()+" , ");
//			}
//			leaves.usedLeaves = appliLeaves;
//			leaves.remainingLeaves = leaves.addedLeaves - appliLeaves;
//			leaves.update();
//			
//		}
////		Logger.debug("Users >>>>>> "+appAdded);
//		return ok("Done");
//	}
//	
	
	public static Float getToBeAplliedLeaves(Long id) throws ParseException{
		SimpleDateFormat sf = new SimpleDateFormat("yyyy");
		SimpleDateFormat lastSF = new SimpleDateFormat("dd-MM-yyyy");
		Date lastDate = lastSF.parse("31-03-2016"); //starting BB8
		String year = sf.format(new Date());
		Float TO_BE_APPLIED = 0.0f;
		String dates = "";
		List<Attendance> attendanceAbsentList = Attendance.find.where().eq("app_user_id",id).eq("status", AttendenceStatus.Absent).findList();
		for(Attendance attendance : attendanceAbsentList){
			if(year.equals(sf.format(attendance.date)) && lastDate.before(lastSF.parse((lastSF.format(attendance.date))))){
				List<DateWiseAppliedLeaves> dateWiseAppliedLeavesToday = DateWiseAppliedLeaves.find.where().eq("leaveDate", EngineerController.getTodayDate(attendance.date)).findList();
				List<AppliedLeaves> appliedLeavesLists = AppliedLeaves.find.where().eq("app_user_id",id).in("dateLeaves",dateWiseAppliedLeavesToday).findList();
				if(appliedLeavesLists.isEmpty()){
					TO_BE_APPLIED++;
					dates = dates+lastSF.format(attendance.date)+",";
				}
			}
		}
		session("tobedates", dates);
		return TO_BE_APPLIED;
	}
	
	public Result toBeApplyLeaveSendMAil(Long Id) {
		AppUser appUser = AppUser.find.byId(Id);
		
		Properties props = new Properties();
		props.put("mail.smtp.host", "smtp.gmail.com");
		props.put("mail.smtp.port", "465");
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.socketFactory.class",
				"javax.net.ssl.SSLSocketFactory");
		
		try {
			// create Session obj
			Authenticator auth = new SMTPAuthenticator();

			Session session = Session.getInstance(props, auth);

			// prepare mail msg
			MimeMessage msg = new MimeMessage(session);
			// set header values
			msg.setSubject("Reminder to apply for unplanned leave");
			msg.setFrom(new InternetAddress(Constants.EMAIL_USERNAME));
			msg.addRecipient(Message.RecipientType.TO, new InternetAddress(appUser.getEmail()));
			
			MimeBodyPart messageBodyPart1 = new MimeBodyPart();
			StringBuilder textMessage = new StringBuilder();
			textMessage.append("<html><Body>Hi "+appUser.getFullName()+",<br><br>");
			textMessage.append("You are requested to apply for "+getToBeAplliedLeaves(Id)+" days of leave, i.e "+session("tobedates")+" when you were absent. Kindly do it at the earliest to avoid <b>Loss of pay</b>. ");
			textMessage.append("<br><br>Thanks,");
			textMessage.append("<br>HR Team");
			textMessage.append("<br>Thrymr Software");
			textMessage.append("</Body></html>");
			messageBodyPart1.setContent(textMessage.toString(), "text/html");
			Multipart multipart = new MimeMultipart();
			multipart.addBodyPart(messageBodyPart1);
			msg.setContent(multipart);

			Transport.send(msg);
		}// try
		catch (Exception ex) {
			ex.printStackTrace();
		}
		
		return ok("To Be Applied Leave Mail successfully sent to "+appUser.getFullName());
	}
}
