/**
* The contents of this file are subject to the Mozilla Public License
* Version 1.1 (the "License"); you may not use this file except in
* compliance with the License. You may obtain a copy of the License at
* http://www.mozilla.org/MPL/
*
* Software distributed under the License is distributed on an "AS IS"
* basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
* License for the specific language governing rights and limitations under
* the License.
*
* The Original Code is OpenELIS code.
*
* Copyright (C) The Minnesota Department of Health.  All Rights Reserved.
*/
package us.mn.state.health.lims.panel.daoimpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.commons.beanutils.PropertyUtils;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import us.mn.state.health.lims.audittrail.dao.AuditTrailDAO;
import us.mn.state.health.lims.common.action.IActionConstants;
import us.mn.state.health.lims.common.daoimpl.BaseDAOImpl;
import us.mn.state.health.lims.common.exception.LIMSDuplicateRecordException;
import us.mn.state.health.lims.common.exception.LIMSRuntimeException;
import us.mn.state.health.lims.common.log.LogEvent;
import us.mn.state.health.lims.common.util.StringUtil;
import us.mn.state.health.lims.common.util.SystemConfiguration;
import us.mn.state.health.lims.panel.dao.PanelDAO;
import us.mn.state.health.lims.panel.valueholder.Panel;

/**
 * @author diane benz
 */
@Component
@Transactional
public class PanelDAOImpl extends BaseDAOImpl<Panel, String> implements PanelDAO {

	public PanelDAOImpl() {
		super(Panel.class);
	}

	@Autowired
	private AuditTrailDAO auditDAO;

	private static Map<String, String> ID_NAME_MAP = null;
	private static Map<String, String> ID_DESCRIPTION_MAP = null;
	private static Map<String, String> NAME_ID_MAP = null;

	@Override
	public void deleteData(List panels) throws LIMSRuntimeException {
		// add to audit trail
		try {

			for (int i = 0; i < panels.size(); i++) {
				Panel data = (Panel) panels.get(i);

				Panel oldData = readPanel(data.getId());
				Panel newData = new Panel();

				String sysUserId = data.getSysUserId();
				String event = IActionConstants.AUDIT_TRAIL_DELETE;
				String tableName = "PANEL";
				auditDAO.saveHistory(newData, oldData, sysUserId, event, tableName);
			}
		} catch (Exception e) {
			// bugzilla 2154
			LogEvent.logError("PanelDAOImpl", "AuditTrail deleteData()", e.toString());
			throw new LIMSRuntimeException("Error in Panel AuditTrail deleteData()", e);
		}

		try {
			for (int i = 0; i < panels.size(); i++) {
				Panel data = (Panel) panels.get(i);
				// bugzilla 2206
				data = readPanel(data.getId());
				sessionFactory.getCurrentSession().delete(data);
				// sessionFactory.getCurrentSession().flush(); // CSL remove old
				// sessionFactory.getCurrentSession().clear(); // CSL remove old
			}
		} catch (Exception e) {
			// bugzilla 2154
			LogEvent.logError("PanelDAOImpl", "deleteData()", e.toString());
			throw new LIMSRuntimeException("Error in Panel deleteData()", e);
		}

		clearIDMaps();
	}

//	@Override
//	public String insert(Panel panel) throws LIMSRuntimeException {
//		try {
//			String id = (String) sessionFactory.getCurrentSession().save(panel);
//			panel.setId(id);
//
//			new AuditTrailDAOImpl().saveNewHistory(panel, panel.getSysUserId(), "PANEL");
//
//			// sessionFactory.getCurrentSession().flush(); // CSL remove old
//			// sessionFactory.getCurrentSession().clear(); // CSL remove old
//			return id;
//		} catch (Exception e) {
//			handleException(e, "insert");
//		}
//		return null;
//	}

	@Override
	public boolean insertData(Panel panel) throws LIMSRuntimeException {
		try {
			// bugzilla 1482 throw Exception if record already exists
			if (duplicatePanelExists(panel)) {
				throw new LIMSDuplicateRecordException("Duplicate record exists for " + panel.getPanelName());
			}

			// AIS - bugzilla 1563
			if (duplicatePanelDescriptionExists(panel)) {
				throw new LIMSDuplicateRecordException("Duplicate record exists for panel description");
			}

			String id = (String) sessionFactory.getCurrentSession().save(panel);
			panel.setId(id);

			// bugzilla 1824 inserts will be logged in history table

			String sysUserId = panel.getSysUserId();
			String tableName = "PANEL";
			auditDAO.saveNewHistory(panel, sysUserId, tableName);

			// sessionFactory.getCurrentSession().flush(); // CSL remove old
			// sessionFactory.getCurrentSession().clear(); // CSL remove old

		} catch (Exception e) {
			// bugzilla 2154
			LogEvent.logError("PanelDAOImpl", "insertData()", e.toString());
			throw new LIMSRuntimeException("Error in Panel insertData()", e);
		}

		clearIDMaps();
		return true;
	}

	@Override
	public void updateData(Panel panel) throws LIMSRuntimeException {
		// bugzilla 1482 throw Exception if record already exists
		try {
			if (duplicatePanelExists(panel)) {
				throw new LIMSDuplicateRecordException("Duplicate record exists for " + panel.getPanelName());
			}
			// AIS - bugzilla 1563
			if (duplicatePanelDescriptionExists(panel)) {
				throw new LIMSDuplicateRecordException("Duplicate record exists for panel description");
			}

		} catch (Exception e) {
			// bugzilla 2154
			LogEvent.logError("PanelDAOImpl", "updateData()", e.toString());
			throw new LIMSRuntimeException("Error in Panel updateData()", e);
		}

//		Panel oldData = readPanel(panel.getId());
//		Panel newData = panel;
//
//		try {
//
//			String sysUserId = panel.getSysUserId();
//			String event = IActionConstants.AUDIT_TRAIL_UPDATE;
//			String tableName = "PANEL";
//			auditDAO.saveHistory(newData, oldData, sysUserId, event, tableName);
//		} catch (Exception e) {
//			// bugzilla 2154
//			LogEvent.logError("PanelDAOImpl", "AuditTrail updateData()", e.toString());
//			throw new LIMSRuntimeException("Error in Panel AuditTrail updateData()", e);
//		}

		try {
			sessionFactory.getCurrentSession().merge(panel);
			// sessionFactory.getCurrentSession().flush(); // CSL remove old
			// sessionFactory.getCurrentSession().clear(); // CSL remove old
			// sessionFactory.getCurrentSession().evict // CSL remove old(panel);
			// sessionFactory.getCurrentSession().refresh // CSL remove old(panel);
		} catch (Exception e) {
			// bugzilla 2154
			LogEvent.logError("PanelDAOImpl", "updateData()", e.toString());
			throw new LIMSRuntimeException("Error in Panel updateData()", e);
		}

		clearIDMaps();
	}

	@Override
	public void getData(Panel panel) throws LIMSRuntimeException {
		try {
			Panel pan = sessionFactory.getCurrentSession().get(Panel.class, panel.getId());
			// sessionFactory.getCurrentSession().flush(); // CSL remove old
			// sessionFactory.getCurrentSession().clear(); // CSL remove old
			if (pan != null) {
				PropertyUtils.copyProperties(panel, pan);
			} else {
				panel.setId(null);
			}
		} catch (Exception e) {
			// bugzilla 2154
			LogEvent.logError("PanelDAOImpl", "getData()", e.toString());
			throw new LIMSRuntimeException("Error in Panel getData()", e);
		}

	}

	@Override
	public Panel getPanelById(String panelId) throws LIMSRuntimeException {
		try {
			Panel panel = sessionFactory.getCurrentSession().get(Panel.class, panelId);
			// closeSession(); // CSL remove old
			return panel;
		} catch (HibernateException e) {
			handleException(e, "getDataById");
		}

		return null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<Panel> getAllActivePanels() throws LIMSRuntimeException {
		try {
			String sql = "from Panel p where p.isActive = 'Y' order by p.panelName";
			org.hibernate.Query query = sessionFactory.getCurrentSession().createQuery(sql);

			List<Panel> list = query.list();
			// sessionFactory.getCurrentSession().flush(); // CSL remove old
			// sessionFactory.getCurrentSession().clear(); // CSL remove old
			return list;
		} catch (Exception e) {
			LogEvent.logError("PanelDAOImpl", "getAllPanels()", e.toString());
			throw new LIMSRuntimeException("Error in Panel getAllActivePanels()", e);
		}

	}

	@Override
	@SuppressWarnings("unchecked")
	public List<Panel> getAllPanels() throws LIMSRuntimeException {
		try {
			String sql = "from Panel p order by p.sortOrderInt";
			org.hibernate.Query query = sessionFactory.getCurrentSession().createQuery(sql);

			List<Panel> list = query.list();
			// sessionFactory.getCurrentSession().flush(); // CSL remove old
			// sessionFactory.getCurrentSession().clear(); // CSL remove old
			return list;
		} catch (Exception e) {
			LogEvent.logError("PanelDAOImpl", "getAllPanels()", e.toString());
			throw new LIMSRuntimeException("Error in Panel getAllPanels()", e);
		}

	}

	@Override
	public List getPageOfPanels(int startingRecNo) throws LIMSRuntimeException {
		List list = new Vector();
		try {
			// calculate maxRow to be one more than the page size
			int endingRecNo = startingRecNo + (SystemConfiguration.getInstance().getDefaultPageSize() + 1);

			// bugzilla 1399
			String sql = "from Panel p order by p.panelName";
			org.hibernate.Query query = sessionFactory.getCurrentSession().createQuery(sql);
			query.setFirstResult(startingRecNo - 1);
			query.setMaxResults(endingRecNo - 1);

			list = query.list();
			// sessionFactory.getCurrentSession().flush(); // CSL remove old
			// sessionFactory.getCurrentSession().clear(); // CSL remove old
		} catch (Exception e) {
			// bugzilla 2154
			LogEvent.logError("PanelDAOImpl", "getPageOfPanels()", e.toString());
			throw new LIMSRuntimeException("Error in Panel getPageOfPanels()", e);
		}

		return list;
	}

	public Panel readPanel(String idString) {
		Panel panel = null;
		try {
			panel = sessionFactory.getCurrentSession().get(Panel.class, idString);
			// sessionFactory.getCurrentSession().flush(); // CSL remove old
			// sessionFactory.getCurrentSession().clear(); // CSL remove old
		} catch (Exception e) {
			// bugzilla 2154
			LogEvent.logError("PanelDAOImpl", "readPanel()", e.toString());
			throw new LIMSRuntimeException("Error in Panel readPanel()", e);
		}

		return panel;
	}

	// this is for autocomplete
	@Override
	public List getActivePanels(String filter) throws LIMSRuntimeException {
		List list = null;
		try {
			String sql = "from Panel p where isActive = 'Y' and upper(p.panelName) like upper(:param) order by upper(p.panelName)";
			org.hibernate.Query query = sessionFactory.getCurrentSession().createQuery(sql);
			query.setParameter("param", filter + "%");

			list = query.list();
			// sessionFactory.getCurrentSession().flush(); // CSL remove old
			// sessionFactory.getCurrentSession().clear(); // CSL remove old
		} catch (Exception e) {
			LogEvent.logError("PanelDAOImpl", "getPanels()", e.toString());
			throw new LIMSRuntimeException("Error in Panel getPanels()", e);
		}
		return list;

	}

	@Override
	public List getNextPanelRecord(String id) throws LIMSRuntimeException {
		return getNextRecord(id, "Panel", Panel.class);
	}

	@Override
	public List getPreviousPanelRecord(String id) throws LIMSRuntimeException {
		return getPreviousRecord(id, "Panel", Panel.class);
	}

	@Override
	public Panel getPanelByName(Panel panel) throws LIMSRuntimeException {
		return getPanelByName(panel.getPanelName());
	}

	@Override
	public Integer getTotalPanelCount() throws LIMSRuntimeException {
		return getTotalCount("Panel", Panel.class);
	}

	@Override
	public List getNextRecord(String id, String table, Class clazz) throws LIMSRuntimeException {

		List list = new Vector();
		try {
			String sql = "from " + table + " t where name >= " + enquote(id) + " order by t.panelName";
			org.hibernate.Query query = sessionFactory.getCurrentSession().createQuery(sql);
			query.setFirstResult(1);
			query.setMaxResults(2);

			list = query.list();

		} catch (Exception e) {
			LogEvent.logError("PanelDAOImpl", "getNextRecord()", e.toString());
			throw new LIMSRuntimeException("Error in getNextRecord() for " + table, e);
		}

		return list;
	}

	@Override
	public List getPreviousRecord(String id, String table, Class clazz) throws LIMSRuntimeException {

		List list = new Vector();
		try {
			String sql = "from " + table + " t order by t.panelName desc where name <= " + enquote(id);
			org.hibernate.Query query = sessionFactory.getCurrentSession().createQuery(sql);
			query.setFirstResult(1);
			query.setMaxResults(2);

			list = query.list();
		} catch (Exception e) {
			LogEvent.logError("PanelDAOImpl", "getPreviousRecord()", e.toString());
			throw new LIMSRuntimeException("Error in getPreviousRecord() for " + table, e);
		}

		return list;
	}

	@Override
	public boolean duplicatePanelExists(Panel panel) throws LIMSRuntimeException {
		try {

			List list = new ArrayList();

			// not case sensitive hemolysis and Hemolysis are considered
			// duplicates
			String sql = "from Panel t where trim(lower(t.panelName)) = :param and t.id != :panelId";
			org.hibernate.Query query = sessionFactory.getCurrentSession().createQuery(sql);
			query.setParameter("param", panel.getPanelName().toLowerCase().trim());

			// initialize with 0 (for new records where no id has been generated
			// yet
			String panelId = "0";
			if (!StringUtil.isNullorNill(panel.getId())) {
				panelId = panel.getId();
			}
			query.setInteger("panelId", Integer.parseInt(panelId));

			list = query.list();
			// sessionFactory.getCurrentSession().flush(); // CSL remove old
			// sessionFactory.getCurrentSession().clear(); // CSL remove old

			if (list.size() > 0) {
				return true;
			} else {
				return false;
			}

		} catch (Exception e) {
			LogEvent.logError("PanelDAOImpl", "duplicatePanelExists()", e.toString());
			throw new LIMSRuntimeException("Error in duplicatePanelExists()", e);
		}
	}

	@Override
	public boolean duplicatePanelDescriptionExists(Panel panel) throws LIMSRuntimeException {
		try {

			List list = new ArrayList();

			// not case sensitive hemolysis and Hemolysis are considered
			// duplicates
			String sql = "from Panel t where trim(lower(t.description)) = :param and t.id != :panelId";
			org.hibernate.Query query = sessionFactory.getCurrentSession().createQuery(sql);
			query.setParameter("param", panel.getDescription().toLowerCase().trim());

			// initialize with 0 (for new records where no id has been generated
			// yet
			String panelId = "0";
			if (!StringUtil.isNullorNill(panel.getId())) {
				panelId = panel.getId();
			}
			query.setInteger("panelId", Integer.parseInt(panelId));

			list = query.list();
			// sessionFactory.getCurrentSession().flush(); // CSL remove old
			// sessionFactory.getCurrentSession().clear(); // CSL remove old

			if (list.size() > 0) {
				return true;
			} else {
				return false;
			}

		} catch (Exception e) {
			LogEvent.logError("PanelDAOImpl", "duplicatePanelDescriptionExists()", e.toString());
			throw new LIMSRuntimeException("Error in duplicatePanelDescriptionExists()", e);
		}
	}

	@Override
	public String getNameForPanelId(String id) {
		if (ID_NAME_MAP == null) {
			loadMaps();
		}

		return ID_NAME_MAP != null ? ID_NAME_MAP.get(id) : id;
	}

	@Override
	public String getDescriptionForPanelId(String id) {
		if (ID_DESCRIPTION_MAP == null) {
			loadMaps();
		}

		return ID_DESCRIPTION_MAP != null ? ID_DESCRIPTION_MAP.get(id) : id;
	}

	@Override
	public String getIdForPanelName(String name) {
		if (NAME_ID_MAP == null) {
			loadMaps();
		}

		return NAME_ID_MAP != null ? NAME_ID_MAP.get(name) : null;
	}

	private void loadMaps() {
		List allPanels = getAllActivePanels();

		if (allPanels != null) {
			ID_NAME_MAP = new HashMap<>();
			ID_DESCRIPTION_MAP = new HashMap<>();
			NAME_ID_MAP = new HashMap<>();

			for (Object panelObj : allPanels) {
				Panel panel = (Panel) panelObj;
				ID_NAME_MAP.put(panel.getId(), panel.getPanelName());
				ID_DESCRIPTION_MAP.put(panel.getId(), panel.getDescription());
				NAME_ID_MAP.put(panel.getPanelName(), panel.getId());
			}
		}
	}

	@Override
	public void clearIDMaps() {
		ID_NAME_MAP = null;
		ID_DESCRIPTION_MAP = null;
	}

	@Override
	public Panel getPanelByName(String panelName) {
		try {
			String sql = "from Panel p where p.panelName = :name";
			Query query = sessionFactory.getCurrentSession().createQuery(sql);
			query.setString("name", panelName);

			@SuppressWarnings("unchecked")
			List<Panel> panelList = query.list();

			// closeSession(); // CSL remove old

			return panelList.isEmpty() ? null : panelList.get(0);

		} catch (Exception e) {
			LogEvent.logError("PanelDAOImpl", "getPanelByName()", e.toString());
			throw new LIMSRuntimeException("Error in Panel getPanelByName()", e);
		}
	}
}