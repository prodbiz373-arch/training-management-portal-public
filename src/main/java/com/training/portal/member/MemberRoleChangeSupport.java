package com.training.portal.member;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import com.training.portal.auth.RoleSessionGuard;
import com.training.portal.schedule.ScheduleCanEditRepository;
import com.training.portal.member.exception.MemberRoleChangeNotAllowedException;

@Component
public class MemberRoleChangeSupport {
    private final JdbcTemplate jdbc;
    private final RoleSessionGuard guard;
    private final ScheduleCanEditRepository permissions;
    public MemberRoleChangeSupport(JdbcTemplate jdbc, RoleSessionGuard guard, ScheduleCanEditRepository permissions) {
        this.jdbc = jdbc; this.guard = guard; this.permissions = permissions;
    }
    public void change(String id, short nextType, String actor) {
        guard.lockWrites();
        var rows = permissions.findByIdSyainIdOrderByIdScheduleIdAsc(id);
        if (nextType == 1 && jdbc.queryForObject(
                "select count(*) from schedule_details where koshi_syain_id=?", Long.class, id) > 0) {
            throw new MemberRoleChangeNotAllowedException();
        }
        if (nextType == 2 && Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from issue_submission_statuses where sinjin_syain_id=? and task_status_kbn<>1)
                    or exists(select 1 from issue_submission_statuses_comment where sinjin_syain_id=?)
                """, Boolean.class, id, id))) {
            throw new MemberRoleChangeNotAllowedException();
        }
        for (var row : rows) {
            String schedule = row.getId().getScheduleId();
            var counts = jdbc.queryForMap("""
                    select count(*) filter (where (case when m.syain_id=? then ? else m.syain_kbn end)=1) as trainees,
                           count(*) filter (where (case when m.syain_id=? then ? else m.syain_kbn end)=2 and p.can_edit=1) as instructors
                    from schedule_can_edit p join kenshu_member m on m.syain_id=p.syain_id
                    where p.schedule_id=?
                    """, id, nextType, id, nextType, schedule);
            if (((Number) counts.get("trainees")).longValue() < 1
                    || ((Number) counts.get("instructors")).longValue() < 1) {
                throw new MemberRoleChangeNotAllowedException();
            }
        }
        rows.forEach(row -> row.synchronizeMemberType(nextType, actor));
        permissions.flush();
    }
}
