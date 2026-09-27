/* =======================================================
	Copyright 2026 - ePortfolium - Licensed under the
	Educational Community License, Version 2.0 (the "License"); you may
	not use this file except in compliance with the License. You may
	obtain a copy of the License at

	http://www.osedu.org/licenses/ECL-2.0

	Unless required by applicable law or agreed to in writing,
	software distributed under the License is distributed on an "AS IS"
	BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
	or implied. See the License for the specific language governing
	permissions and limitations under the License.
   ======================================================= */

package com.eportfolium.karuta.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eportfolium.karuta.entity.SMSRegistrationEntity;
import java.util.UUID;

public interface SMSRegistrationRepository extends JpaRepository<SMSRegistrationEntity, Long> {

    SMSRegistrationEntity save( SMSRegistrationEntity registration );

    void delete( SMSRegistrationEntity registration );
    
    boolean existsByUserId(Long userId);

    SMSRegistrationEntity findByUserId(Long userId);

    SMSRegistrationEntity findByRequestId(UUID uuid);
}
