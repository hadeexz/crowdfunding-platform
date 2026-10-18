/* =====================================================================
   GiveLivesDB - schema script (run top to bottom, safe to re-run)
   Order matters because of foreign keys (parents before children):
     1. users
     2. creator_verifications  (needs users)
     3. campaigns              (needs users)
     4. donations              (needs campaigns, users)
     5. payment_transactions   (needs donations)
     6. payouts                (needs campaigns, users)
     7. admin_actions          (needs users)
     8. webhook_events         (independent)
   ===================================================================== */

-- Needed for filtered indexes and CHECK constraints to behave consistently
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

IF DB_ID(N'GiveLivesDB') IS NULL
BEGIN
    CREATE DATABASE GiveLivesDB;
END
GO

USE GiveLivesDB;
GO

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

/* ---------------------------------------------------------------
   DEV ONLY RESET: uncomment, run once, then comment again.
   Drop in REVERSE order of creation (children first).

   DROP TABLE IF EXISTS dbo.webhook_events;
   DROP TABLE IF EXISTS dbo.admin_actions;
   DROP TABLE IF EXISTS dbo.payouts;
   DROP TABLE IF EXISTS dbo.payment_transactions;
   DROP TABLE IF EXISTS dbo.donations;
   DROP TABLE IF EXISTS dbo.campaigns;
   DROP TABLE IF EXISTS dbo.creator_verifications;
   DROP TABLE IF EXISTS dbo.users;
---------------------------------------------------------------- */


/* =====================================================================
   1. USERS
   ===================================================================== */
IF OBJECT_ID(N'dbo.users', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.users (
        id BIGINT IDENTITY(1,1) NOT NULL,

        full_name NVARCHAR(150) NOT NULL,

        -- CI = case-insensitive, so email uniqueness ignores letter case
        email NVARCHAR(254) COLLATE Latin1_General_100_CI_AS NOT NULL,

        password_hash NVARCHAR(255) NOT NULL,

        -- ADMIN accounts are created manually (never through public signup)
        account_type VARCHAR(20) NOT NULL,

        [role] VARCHAR(10) NOT NULL
            CONSTRAINT DF_users_role DEFAULT 'USER',

        -- No default: the application must set the correct status at registration
        verification_status VARCHAR(20) NOT NULL,

        is_banned BIT NOT NULL
            CONSTRAINT DF_users_is_banned DEFAULT 0,

        ban_reason NVARCHAR(500) NULL,

        created_at DATETIME2(3) NOT NULL
            CONSTRAINT DF_users_created_at DEFAULT SYSUTCDATETIME(),

        updated_at DATETIME2(3) NOT NULL
            CONSTRAINT DF_users_updated_at DEFAULT SYSUTCDATETIME(),

        CONSTRAINT PK_users PRIMARY KEY (id),

        -- Also creates the required email index
        CONSTRAINT UQ_users_email UNIQUE (email),

        CONSTRAINT CK_users_account_type
            CHECK (account_type IN ('DONOR', 'CREATOR', 'ORGANIZATION', 'ADMIN')),

        CONSTRAINT CK_users_role
            CHECK ([role] IN ('USER', 'ADMIN')),

        -- Admin account type and admin role always go together
        CONSTRAINT CK_users_admin_role
            CHECK (
                (account_type = 'ADMIN' AND [role] = 'ADMIN')
                OR
                (account_type <> 'ADMIN' AND [role] = 'USER')
            ),

        CONSTRAINT CK_users_verification_status
            CHECK (verification_status IN (
                'NOT_REQUIRED',
                'NOT_SUBMITTED',
                'PENDING',
                'VERIFIED',
                'REJECTED'
            )),

        -- Donors and admins do not need KYC.
        -- Creators and organizations must follow the KYC process.
        -- NOTE: a donor -> creator upgrade must change account_type and
        -- verification_status in ONE UPDATE statement.
        CONSTRAINT CK_users_account_verification
            CHECK (
                (account_type IN ('DONOR', 'ADMIN')
                    AND verification_status = 'NOT_REQUIRED')
                OR
                (account_type IN ('CREATOR', 'ORGANIZATION')
                    AND verification_status IN (
                        'NOT_SUBMITTED',
                        'PENDING',
                        'VERIFIED',
                        'REJECTED'
                    ))
            ),

        -- A ban must have a reason; an unbanned user cannot retain one
        CONSTRAINT CK_users_ban_reason
            CHECK (
                (is_banned = 0 AND ban_reason IS NULL)
                OR
                (
                    is_banned = 1
                    AND LEN(LTRIM(RTRIM(ISNULL(ban_reason, '')))) > 0
                )
            )
    );
END
GO

-- Keeps updated_at current. Do NOT also use @UpdateTimestamp in Spring.
CREATE OR ALTER TRIGGER dbo.TR_users_set_updated_at
ON dbo.users
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    -- Skip only when THIS trigger is already running (self-recursion guard)
    IF TRIGGER_NESTLEVEL(OBJECT_ID(N'dbo.TR_users_set_updated_at'), 'AFTER', 'DML') > 1
        RETURN;

    UPDATE u
    SET updated_at = SYSUTCDATETIME()
    FROM dbo.users AS u
    INNER JOIN inserted AS i ON i.id = u.id;
END;
GO


/* =====================================================================
   2. CREATOR_VERIFICATIONS  (KYC submissions, one row per submission)
   - A rejected user can resubmit, so history is kept as extra rows.
   - Only ONE non-rejected (PENDING/APPROVED) submission per user at a time.
   - Service layer must, in ONE transaction, also update
     users.verification_status when a submission is approved/rejected.
   - Service layer must check that reviewed_by is an ADMIN user.
   - Store only a MASKED document number (e.g. XXXX-XXXX-1234), never the
     full Aadhaar/PAN/passport number.
   ===================================================================== */
IF OBJECT_ID(N'dbo.creator_verifications', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.creator_verifications (
        id BIGINT IDENTITY(1,1) NOT NULL,

        user_id BIGINT NOT NULL,

        document_type VARCHAR(30) NOT NULL,
        document_number_masked VARCHAR(30) NOT NULL,
        document_file_url NVARCHAR(500) NULL,

        [status] VARCHAR(10) NOT NULL
            CONSTRAINT DF_creator_verifications_status DEFAULT 'PENDING',

        submitted_at DATETIME2(3) NOT NULL
            CONSTRAINT DF_creator_verifications_submitted_at DEFAULT SYSUTCDATETIME(),

        reviewed_by BIGINT NULL,
        reviewed_at DATETIME2(3) NULL,
        remarks NVARCHAR(500) NULL,

        CONSTRAINT PK_creator_verifications PRIMARY KEY (id),

        CONSTRAINT FK_creator_verifications_user
            FOREIGN KEY (user_id) REFERENCES dbo.users (id),

        CONSTRAINT FK_creator_verifications_reviewer
            FOREIGN KEY (reviewed_by) REFERENCES dbo.users (id),

        CONSTRAINT CK_creator_verifications_document_type
            CHECK (document_type IN (
                'AADHAAR',
                'PAN',
                'PASSPORT',
                'VOTER_ID',
                'DRIVING_LICENSE',
                'ORG_REGISTRATION'
            )),

        CONSTRAINT CK_creator_verifications_document_number
            CHECK (LEN(LTRIM(RTRIM(document_number_masked))) > 0),

        CONSTRAINT CK_creator_verifications_status
            CHECK ([status] IN ('PENDING', 'APPROVED', 'REJECTED')),

        -- Review fields must match the status
        CONSTRAINT CK_creator_verifications_review
            CHECK (
                ([status] = 'PENDING'
                    AND reviewed_by IS NULL
                    AND reviewed_at IS NULL)
                OR
                ([status] = 'APPROVED'
                    AND reviewed_by IS NOT NULL
                    AND reviewed_at IS NOT NULL)
                OR
                ([status] = 'REJECTED'
                    AND reviewed_by IS NOT NULL
                    AND reviewed_at IS NOT NULL
                    AND LEN(LTRIM(RTRIM(ISNULL(remarks, '')))) > 0)
            ),

        -- Nobody can review their own submission
        CONSTRAINT CK_creator_verifications_not_self
            CHECK (reviewed_by IS NULL OR reviewed_by <> user_id)
    );
END
GO

-- One active (PENDING or APPROVED) submission per user
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'UX_creator_verifications_active_user'
      AND object_id = OBJECT_ID(N'dbo.creator_verifications')
)
BEGIN
    CREATE UNIQUE INDEX UX_creator_verifications_active_user
        ON dbo.creator_verifications (user_id)
        WHERE [status] <> 'REJECTED';
END
GO

-- Admin review queue: "show me all PENDING, oldest first"
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_creator_verifications_status_submitted'
      AND object_id = OBJECT_ID(N'dbo.creator_verifications')
)
BEGIN
    CREATE INDEX IX_creator_verifications_status_submitted
        ON dbo.creator_verifications ([status], submitted_at);
END
GO


/* =====================================================================
   3. CAMPAIGNS
   Rules enforced by the service layer (cannot be done by CHECK):
   - creator_id must be a CREATOR/ORGANIZATION with verification_status = 'VERIFIED'
   - suspended_by must be an ADMIN
   - after the first donation, only goal_amount may be INCREASED
   - a donation larger than the remaining amount must be rejected/capped
   - when current_amount reaches goal_amount set status = 'GOAL_REACHED'
     in the same transaction; when the creator raises the goal, set it back
     to 'ACTIVE'
   ===================================================================== */
IF OBJECT_ID(N'dbo.campaigns', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.campaigns (
        id BIGINT IDENTITY(1,1) NOT NULL,

        creator_id BIGINT NOT NULL,

        title NVARCHAR(150) NOT NULL,
        [description] NVARCHAR(MAX) NOT NULL,
        category VARCHAR(30) NOT NULL,

        -- Minimum goal enforced below (1000 is a placeholder, change if needed); no maximum
        goal_amount DECIMAL(12,2) NOT NULL,

        current_amount DECIMAL(12,2) NOT NULL
            CONSTRAINT DF_campaigns_current_amount DEFAULT 0,

        [status] VARCHAR(20) NOT NULL
            CONSTRAINT DF_campaigns_status DEFAULT 'DRAFT',

        -- Set when the campaign becomes ACTIVE; NULL while DRAFT
        start_date DATETIME2(3) NULL,
        deadline DATETIME2(3) NOT NULL,

        -- Admin suspension details (kept for history after refund)
        suspended_by BIGINT NULL,
        suspended_at DATETIME2(3) NULL,
        suspension_reason NVARCHAR(500) NULL,

        -- Optimistic locking for Spring: @Version private Long version;
        [version] BIGINT NOT NULL
            CONSTRAINT DF_campaigns_version DEFAULT 0,

        created_at DATETIME2(3) NOT NULL
            CONSTRAINT DF_campaigns_created_at DEFAULT SYSUTCDATETIME(),

        updated_at DATETIME2(3) NOT NULL
            CONSTRAINT DF_campaigns_updated_at DEFAULT SYSUTCDATETIME(),

        CONSTRAINT PK_campaigns PRIMARY KEY (id),

        CONSTRAINT FK_campaigns_creator
            FOREIGN KEY (creator_id) REFERENCES dbo.users (id),

        CONSTRAINT FK_campaigns_suspended_by
            FOREIGN KEY (suspended_by) REFERENCES dbo.users (id),

        CONSTRAINT CK_campaigns_title
            CHECK (LEN(LTRIM(RTRIM(title))) > 0),

        CONSTRAINT CK_campaigns_category
            CHECK (category IN (
                'MEDICAL',
                'EDUCATION',
                'DISASTER_RELIEF',
                'ANIMAL_WELFARE',
                'COMMUNITY',
                'ENVIRONMENT',
                'OTHER'
            )),

        CONSTRAINT CK_campaigns_goal_min
            CHECK (goal_amount >= 1000),

        -- Donations stop at the goal, so current can never exceed goal
        CONSTRAINT CK_campaigns_current_amount
            CHECK (current_amount >= 0 AND current_amount <= goal_amount),

        CONSTRAINT CK_campaigns_status
            CHECK ([status] IN (
                'DRAFT',
                'ACTIVE',
                'GOAL_REACHED',
                'SUCCESSFUL',
                'FAILED',
                'SUSPENDED',
                'REFUNDED'
            )),

        CONSTRAINT CK_campaigns_dates
            CHECK (
                deadline > created_at
                AND (start_date IS NULL OR deadline > start_date)
            ),

        -- DRAFT has no start date; every other status must have one
        CONSTRAINT CK_campaigns_start_date
            CHECK (
                ([status] = 'DRAFT' AND start_date IS NULL)
                OR
                ([status] <> 'DRAFT' AND start_date IS NOT NULL)
            ),

        -- Status must agree with the money collected
        CONSTRAINT CK_campaigns_progress
            CHECK (
                ([status] = 'DRAFT' AND current_amount = 0)
                OR ([status] = 'ACTIVE' AND current_amount < goal_amount)
                OR ([status] IN ('GOAL_REACHED', 'SUCCESSFUL')
                        AND current_amount = goal_amount)
                OR ([status] = 'FAILED' AND current_amount < goal_amount)
                OR [status] IN ('SUSPENDED', 'REFUNDED')
            ),

        -- Suspension fields are all set together or all empty
        CONSTRAINT CK_campaigns_suspension_fields
            CHECK (
                (suspended_by IS NULL
                    AND suspended_at IS NULL
                    AND suspension_reason IS NULL)
                OR
                (suspended_by IS NOT NULL
                    AND suspended_at IS NOT NULL
                    AND LEN(LTRIM(RTRIM(ISNULL(suspension_reason, '')))) > 0)
            ),

        -- A SUSPENDED campaign must carry its suspension details
        CONSTRAINT CK_campaigns_suspended_status
            CHECK ([status] <> 'SUSPENDED' OR suspended_by IS NOT NULL)
    );
END
GO

-- Deadline scheduler: "ACTIVE/GOAL_REACHED campaigns whose deadline has passed"
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_campaigns_status_deadline'
      AND object_id = OBJECT_ID(N'dbo.campaigns')
)
BEGIN
    CREATE INDEX IX_campaigns_status_deadline
        ON dbo.campaigns ([status], deadline)
        INCLUDE (goal_amount, current_amount);
END
GO

-- "My campaigns" page
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_campaigns_creator_id'
      AND object_id = OBJECT_ID(N'dbo.campaigns')
)
BEGIN
    CREATE INDEX IX_campaigns_creator_id
        ON dbo.campaigns (creator_id);
END
GO

-- Browse by category (active campaigns)
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_campaigns_category_status'
      AND object_id = OBJECT_ID(N'dbo.campaigns')
)
BEGIN
    CREATE INDEX IX_campaigns_category_status
        ON dbo.campaigns (category, [status]);
END
GO

-- Keeps updated_at current. (version is managed by Hibernate, not this trigger.)
CREATE OR ALTER TRIGGER dbo.TR_campaigns_set_updated_at
ON dbo.campaigns
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF TRIGGER_NESTLEVEL(OBJECT_ID(N'dbo.TR_campaigns_set_updated_at'), 'AFTER', 'DML') > 1
        RETURN;

    UPDATE c
    SET updated_at = SYSUTCDATETIME()
    FROM dbo.campaigns AS c
    INNER JOIN inserted AS i ON i.id = c.id;
END;
GO
/* =====================================================================
   4. DONATIONS
   Rules enforced by the service layer (cannot be done by CHECK):
   - campaign must be ACTIVE and not past its deadline
   - donor must not be banned
   - amount must not exceed the campaign's remaining amount
   - campaigns.current_amount is increased only when the donation becomes
     COMPLETED (and decreased/handled when it becomes REFUNDED), in the
     same transaction
   - is_anonymous only hides the donor's name in public APIs; the donor_id
     is always stored (needed for refunds and audit)
   ===================================================================== */
IF OBJECT_ID(N'dbo.donations', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.donations (
        id BIGINT IDENTITY(1,1) NOT NULL,

        campaign_id BIGINT NOT NULL,
        donor_id BIGINT NOT NULL,

        amount DECIMAL(12,2) NOT NULL,

        is_anonymous BIT NOT NULL
            CONSTRAINT DF_donations_is_anonymous DEFAULT 0,

        [status] VARCHAR(10) NOT NULL
            CONSTRAINT DF_donations_status DEFAULT 'PENDING',

        -- Set when payment is confirmed; kept after a later refund
        completed_at DATETIME2(3) NULL,

        created_at DATETIME2(3) NOT NULL
            CONSTRAINT DF_donations_created_at DEFAULT SYSUTCDATETIME(),

        updated_at DATETIME2(3) NOT NULL
            CONSTRAINT DF_donations_updated_at DEFAULT SYSUTCDATETIME(),

        CONSTRAINT PK_donations PRIMARY KEY (id),

        CONSTRAINT FK_donations_campaign
            FOREIGN KEY (campaign_id) REFERENCES dbo.campaigns (id),

        CONSTRAINT FK_donations_donor
            FOREIGN KEY (donor_id) REFERENCES dbo.users (id),

        CONSTRAINT CK_donations_amount
            CHECK (amount > 0),

        CONSTRAINT CK_donations_status
            CHECK ([status] IN ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED')),

        -- COMPLETED and REFUNDED were paid, so they have a completion time;
        -- PENDING and FAILED never completed
        CONSTRAINT CK_donations_completed_at
            CHECK (
                ([status] IN ('PENDING', 'FAILED') AND completed_at IS NULL)
                OR
                ([status] IN ('COMPLETED', 'REFUNDED') AND completed_at IS NOT NULL)
            )
    );
END
GO

-- Sum of COMPLETED donations per campaign, donor lists per campaign
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_donations_campaign_status'
      AND object_id = OBJECT_ID(N'dbo.donations')
)
BEGIN
    CREATE INDEX IX_donations_campaign_status
        ON dbo.donations (campaign_id, [status])
        INCLUDE (amount, donor_id, is_anonymous);
END
GO

-- "My donations" page
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_donations_donor_id'
      AND object_id = OBJECT_ID(N'dbo.donations')
)
BEGIN
    CREATE INDEX IX_donations_donor_id
        ON dbo.donations (donor_id, created_at DESC);
END
GO

-- Cleanup job: find old PENDING donations that were never paid
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_donations_status_created'
      AND object_id = OBJECT_ID(N'dbo.donations')
)
BEGIN
    CREATE INDEX IX_donations_status_created
        ON dbo.donations ([status], created_at);
END
GO

CREATE OR ALTER TRIGGER dbo.TR_donations_set_updated_at
ON dbo.donations
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF TRIGGER_NESTLEVEL(OBJECT_ID(N'dbo.TR_donations_set_updated_at'), 'AFTER', 'DML') > 1
        RETURN;

    UPDATE d
    SET updated_at = SYSUTCDATETIME()
    FROM dbo.donations AS d
    INNER JOIN inserted AS i ON i.id = d.id;
END;
GO


/* =====================================================================
   5. PAYMENT_TRANSACTIONS  (Razorpay side, exactly one per donation)
   Rules enforced by the service layer (cannot be done by CHECK):
   - amount must equal the donation's amount
   - platform_fee is calculated by the service (10% per your rules), so the
     fee percentage is not hard-coded in the database
   - Razorpay works in paise (amount x 100); convert in Java, store rupees here
   - verify the Razorpay signature / webhook before setting SUCCESS
   - donations.status and payment_transactions.status must be updated together
     in one transaction
   ===================================================================== */
IF OBJECT_ID(N'dbo.payment_transactions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.payment_transactions (
        id BIGINT IDENTITY(1,1) NOT NULL,

        donation_id BIGINT NOT NULL,

        razorpay_order_id VARCHAR(50) NOT NULL,
        razorpay_payment_id VARCHAR(50) NULL,
        razorpay_signature VARCHAR(255) NULL,

        currency CHAR(3) NOT NULL
            CONSTRAINT DF_payment_transactions_currency DEFAULT 'INR',

        amount DECIMAL(12,2) NOT NULL,
        platform_fee DECIMAL(12,2) NOT NULL,
        net_amount DECIMAL(12,2) NOT NULL,

        [status] VARCHAR(20) NOT NULL
            CONSTRAINT DF_payment_transactions_status DEFAULT 'CREATED',

        failure_reason NVARCHAR(500) NULL,

        paid_at DATETIME2(3) NULL,

        razorpay_refund_id VARCHAR(50) NULL,
        refund_amount DECIMAL(12,2) NULL,
        refunded_at DATETIME2(3) NULL,

        created_at DATETIME2(3) NOT NULL
            CONSTRAINT DF_payment_transactions_created_at DEFAULT SYSUTCDATETIME(),

        updated_at DATETIME2(3) NOT NULL
            CONSTRAINT DF_payment_transactions_updated_at DEFAULT SYSUTCDATETIME(),

        CONSTRAINT PK_payment_transactions PRIMARY KEY (id),

        -- One donation = one transaction
        CONSTRAINT UQ_payment_transactions_donation UNIQUE (donation_id),

        CONSTRAINT UQ_payment_transactions_order UNIQUE (razorpay_order_id),

        CONSTRAINT FK_payment_transactions_donation
            FOREIGN KEY (donation_id) REFERENCES dbo.donations (id),

        CONSTRAINT CK_payment_transactions_amounts
            CHECK (
                amount > 0
                AND platform_fee >= 0
                AND net_amount >= 0
                AND platform_fee + net_amount = amount
            ),

        CONSTRAINT CK_payment_transactions_status
            CHECK ([status] IN (
                'CREATED',
                'SUCCESS',
                'FAILED',
                'REFUND_INITIATED',
                'REFUNDED'
            )),

        -- Every status must carry exactly the fields that make sense for it
        CONSTRAINT CK_payment_transactions_state
            CHECK (
                ([status] = 'CREATED'
                    AND razorpay_payment_id IS NULL
                    AND paid_at IS NULL
                    AND failure_reason IS NULL
                    AND razorpay_refund_id IS NULL
                    AND refund_amount IS NULL
                    AND refunded_at IS NULL)
                OR
                ([status] = 'SUCCESS'
                    AND razorpay_payment_id IS NOT NULL
                    AND paid_at IS NOT NULL
                    AND failure_reason IS NULL
                    AND razorpay_refund_id IS NULL
                    AND refund_amount IS NULL
                    AND refunded_at IS NULL)
                OR
                ([status] = 'FAILED'
                    AND paid_at IS NULL
                    AND LEN(LTRIM(RTRIM(ISNULL(failure_reason, '')))) > 0
                    AND razorpay_refund_id IS NULL
                    AND refund_amount IS NULL
                    AND refunded_at IS NULL)
                OR
                ([status] = 'REFUND_INITIATED'
                    AND razorpay_payment_id IS NOT NULL
                    AND paid_at IS NOT NULL
                    AND refund_amount IS NOT NULL
                    AND refunded_at IS NULL)
                OR
                ([status] = 'REFUNDED'
                    AND razorpay_payment_id IS NOT NULL
                    AND paid_at IS NOT NULL
                    AND razorpay_refund_id IS NOT NULL
                    AND refund_amount IS NOT NULL
                    AND refunded_at IS NOT NULL)
            ),

        -- A refund can never be more than what was paid
        CONSTRAINT CK_payment_transactions_refund_amount
            CHECK (refund_amount IS NULL OR (refund_amount > 0 AND refund_amount <= amount))
    );
END
GO

-- A Razorpay payment id can belong to only one transaction (NULLs are allowed many times)
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'UX_payment_transactions_payment_id'
      AND object_id = OBJECT_ID(N'dbo.payment_transactions')
)
BEGIN
    CREATE UNIQUE INDEX UX_payment_transactions_payment_id
        ON dbo.payment_transactions (razorpay_payment_id)
        WHERE razorpay_payment_id IS NOT NULL;
END
GO

-- A Razorpay refund id can belong to only one transaction
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'UX_payment_transactions_refund_id'
      AND object_id = OBJECT_ID(N'dbo.payment_transactions')
)
BEGIN
    CREATE UNIQUE INDEX UX_payment_transactions_refund_id
        ON dbo.payment_transactions (razorpay_refund_id)
        WHERE razorpay_refund_id IS NOT NULL;
END
GO

-- Refund job: "all transactions in REFUND_INITIATED", reconciliation by status
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_payment_transactions_status'
      AND object_id = OBJECT_ID(N'dbo.payment_transactions')
)
BEGIN
    CREATE INDEX IX_payment_transactions_status
        ON dbo.payment_transactions ([status], created_at);
END
GO

CREATE OR ALTER TRIGGER dbo.TR_payment_transactions_set_updated_at
ON dbo.payment_transactions
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF TRIGGER_NESTLEVEL(OBJECT_ID(N'dbo.TR_payment_transactions_set_updated_at'), 'AFTER', 'DML') > 1
        RETURN;

    UPDATE p
    SET updated_at = SYSUTCDATETIME()
    FROM dbo.payment_transactions AS p
    INNER JOIN inserted AS i ON i.id = p.id;
END;
GO
/* =====================================================================
   6. PAYOUTS  (money released to the creator after a SUCCESSFUL campaign)
   Rules enforced by the service layer (cannot be done by CHECK):
   - campaign must be SUCCESSFUL and creator_id must equal campaigns.creator_id
   - creator must be VERIFIED and not banned
   - gross_amount must equal the sum of COMPLETED donations of the campaign
   - platform_fee is calculated by the service (10% per your rules)
   - requires_review is set to 1 when payout_amount is above the 50k
     threshold; the threshold is NOT hard-coded here so you can change it
   - reviewed_by must be an ADMIN user
   ===================================================================== */
IF OBJECT_ID(N'dbo.payouts', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.payouts (
        id BIGINT IDENTITY(1,1) NOT NULL,

        campaign_id BIGINT NOT NULL,
        creator_id BIGINT NOT NULL,

        gross_amount DECIMAL(12,2) NOT NULL,
        platform_fee DECIMAL(12,2) NOT NULL,
        payout_amount DECIMAL(12,2) NOT NULL,

        -- 1 = above the due-diligence / anti-money-laundering threshold
        requires_review BIT NOT NULL
            CONSTRAINT DF_payouts_requires_review DEFAULT 0,

        [status] VARCHAR(15) NOT NULL
            CONSTRAINT DF_payouts_status DEFAULT 'PENDING',

        reviewed_by BIGINT NULL,
        reviewed_at DATETIME2(3) NULL,
        remarks NVARCHAR(500) NULL,

        -- Bank / payment-partner transfer reference, set when money is sent
        payout_reference VARCHAR(100) NULL,
        released_at DATETIME2(3) NULL,

        created_at DATETIME2(3) NOT NULL
            CONSTRAINT DF_payouts_created_at DEFAULT SYSUTCDATETIME(),

        updated_at DATETIME2(3) NOT NULL
            CONSTRAINT DF_payouts_updated_at DEFAULT SYSUTCDATETIME(),

        CONSTRAINT PK_payouts PRIMARY KEY (id),

        -- One payout per campaign
        CONSTRAINT UQ_payouts_campaign UNIQUE (campaign_id),

        CONSTRAINT FK_payouts_campaign
            FOREIGN KEY (campaign_id) REFERENCES dbo.campaigns (id),

        CONSTRAINT FK_payouts_creator
            FOREIGN KEY (creator_id) REFERENCES dbo.users (id),

        CONSTRAINT FK_payouts_reviewer
            FOREIGN KEY (reviewed_by) REFERENCES dbo.users (id),

        CONSTRAINT CK_payouts_amounts
            CHECK (
                gross_amount > 0
                AND platform_fee >= 0
                AND payout_amount > 0
                AND platform_fee + payout_amount = gross_amount
            ),

        CONSTRAINT CK_payouts_status
            CHECK ([status] IN (
                'PENDING',
                'UNDER_REVIEW',
                'APPROVED',
                'RELEASED',
                'REJECTED'
            )),

        -- Each status must carry exactly the fields that make sense for it
        CONSTRAINT CK_payouts_state
            CHECK (
                -- waiting to be processed
                ([status] = 'PENDING'
                    AND reviewed_by IS NULL
                    AND reviewed_at IS NULL
                    AND released_at IS NULL
                    AND payout_reference IS NULL)
                OR
                -- big payout waiting for due diligence
                ([status] = 'UNDER_REVIEW'
                    AND requires_review = 1
                    AND reviewed_at IS NULL
                    AND released_at IS NULL
                    AND payout_reference IS NULL)
                OR
                -- approved: a review-required payout needs a reviewer
                ([status] = 'APPROVED'
                    AND released_at IS NULL
                    AND payout_reference IS NULL
                    AND (requires_review = 0
                         OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)))
                OR
                -- released: money has been sent
                ([status] = 'RELEASED'
                    AND released_at IS NOT NULL
                    AND LEN(LTRIM(RTRIM(ISNULL(payout_reference, '')))) > 0
                    AND (requires_review = 0
                         OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)))
                OR
                -- rejected: always by a reviewer, always with a reason
                ([status] = 'REJECTED'
                    AND reviewed_by IS NOT NULL
                    AND reviewed_at IS NOT NULL
                    AND released_at IS NULL
                    AND payout_reference IS NULL
                    AND LEN(LTRIM(RTRIM(ISNULL(remarks, '')))) > 0)
            ),

        -- A reviewer can never approve their own payout
        CONSTRAINT CK_payouts_not_self
            CHECK (reviewed_by IS NULL OR reviewed_by <> creator_id)
    );
END
GO

-- Admin queue: "show payouts UNDER_REVIEW / PENDING, oldest first"
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_payouts_status_created'
      AND object_id = OBJECT_ID(N'dbo.payouts')
)
BEGIN
    CREATE INDEX IX_payouts_status_created
        ON dbo.payouts ([status], created_at);
END
GO

-- "My payouts" page
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_payouts_creator_id'
      AND object_id = OBJECT_ID(N'dbo.payouts')
)
BEGIN
    CREATE INDEX IX_payouts_creator_id
        ON dbo.payouts (creator_id);
END
GO

CREATE OR ALTER TRIGGER dbo.TR_payouts_set_updated_at
ON dbo.payouts
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF TRIGGER_NESTLEVEL(OBJECT_ID(N'dbo.TR_payouts_set_updated_at'), 'AFTER', 'DML') > 1
        RETURN;

    UPDATE p
    SET updated_at = SYSUTCDATETIME()
    FROM dbo.payouts AS p
    INNER JOIN inserted AS i ON i.id = p.id;
END;
GO


/* =====================================================================
   7. ADMIN_ACTIONS  (append-only audit log of everything admins do)
   - target_id points to a row in the table named by target_type. There is
     no foreign key because one column cannot reference several tables.
   - admin_id must belong to an ADMIN user: enforced by the service layer.
   - Rows can never be changed or deleted (trigger below). To clear test
     data in development use the DROP TABLE reset at the top of the file.
   ===================================================================== */
IF OBJECT_ID(N'dbo.admin_actions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.admin_actions (
        id BIGINT IDENTITY(1,1) NOT NULL,

        admin_id BIGINT NOT NULL,

        action_type VARCHAR(40) NOT NULL,
        target_type VARCHAR(30) NOT NULL,
        target_id BIGINT NOT NULL,

        reason NVARCHAR(500) NULL,
        ip_address VARCHAR(45) NULL,

        created_at DATETIME2(3) NOT NULL
            CONSTRAINT DF_admin_actions_created_at DEFAULT SYSUTCDATETIME(),

        CONSTRAINT PK_admin_actions PRIMARY KEY (id),

        CONSTRAINT FK_admin_actions_admin
            FOREIGN KEY (admin_id) REFERENCES dbo.users (id),

        CONSTRAINT CK_admin_actions_action_type
            CHECK (action_type IN (
                'BAN_USER',
                'UNBAN_USER',
                'SUSPEND_CAMPAIGN',
                'UNSUSPEND_CAMPAIGN',
                'APPROVE_CAMPAIGN_REFUND',
                'APPROVE_KYC',
                'REJECT_KYC',
                'APPROVE_PAYOUT',
                'REJECT_PAYOUT',
                'RELEASE_PAYOUT'
            )),

        CONSTRAINT CK_admin_actions_target_type
            CHECK (target_type IN (
                'USER',
                'CAMPAIGN',
                'CREATOR_VERIFICATION',
                'PAYOUT'
            )),

        -- The action must match the kind of thing it acts on
        CONSTRAINT CK_admin_actions_action_target
            CHECK (
                (action_type IN ('BAN_USER', 'UNBAN_USER')
                    AND target_type = 'USER')
                OR
                (action_type IN ('SUSPEND_CAMPAIGN', 'UNSUSPEND_CAMPAIGN',
                                 'APPROVE_CAMPAIGN_REFUND')
                    AND target_type = 'CAMPAIGN')
                OR
                (action_type IN ('APPROVE_KYC', 'REJECT_KYC')
                    AND target_type = 'CREATOR_VERIFICATION')
                OR
                (action_type IN ('APPROVE_PAYOUT', 'REJECT_PAYOUT', 'RELEASE_PAYOUT')
                    AND target_type = 'PAYOUT')
            ),

        -- Serious actions must always explain themselves
        CONSTRAINT CK_admin_actions_reason
            CHECK (
                action_type NOT IN ('BAN_USER', 'SUSPEND_CAMPAIGN',
                                    'REJECT_KYC', 'REJECT_PAYOUT')
                OR LEN(LTRIM(RTRIM(ISNULL(reason, '')))) > 0
            )
    );
END
GO

-- "What did this admin do?"
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_admin_actions_admin'
      AND object_id = OBJECT_ID(N'dbo.admin_actions')
)
BEGIN
    CREATE INDEX IX_admin_actions_admin
        ON dbo.admin_actions (admin_id, created_at DESC);
END
GO

-- "What happened to this user / campaign / payout?"
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_admin_actions_target'
      AND object_id = OBJECT_ID(N'dbo.admin_actions')
)
BEGIN
    CREATE INDEX IX_admin_actions_target
        ON dbo.admin_actions (target_type, target_id, created_at DESC);
END
GO

-- Makes the audit log tamper-proof: no UPDATE and no DELETE, ever
CREATE OR ALTER TRIGGER dbo.TR_admin_actions_append_only
ON dbo.admin_actions
INSTEAD OF UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    THROW 50001, 'admin_actions is an append-only audit log: rows cannot be updated or deleted.', 1;
END;
GO
/* =====================================================================
   8. WEBHOOK_EVENTS  (every Razorpay webhook received, processed once)
   How to use it (idempotency):
   1. Verify the webhook signature first.
   2. INSERT a row with razorpay_event_id from the X-Razorpay-Event-Id header.
   3. If the INSERT fails on UQ_webhook_events_event_id, it is a duplicate
      delivery: return HTTP 200 and do nothing else.
   4. Otherwise process the event, then set processing_status to PROCESSED
      (or FAILED with error_message, or IGNORED for event types you skip).
   - Payloads can grow large; plan a cleanup job (for example delete
     PROCESSED rows older than 90 days).
   ===================================================================== */
IF OBJECT_ID(N'dbo.webhook_events', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.webhook_events (
        id BIGINT IDENTITY(1,1) NOT NULL,

        razorpay_event_id VARCHAR(100) NOT NULL,
        event_type VARCHAR(60) NOT NULL,

        -- Raw JSON body exactly as received
        payload NVARCHAR(MAX) NOT NULL,

        -- Result of the signature check; an invalid one can never be PROCESSED
        signature_valid BIT NOT NULL,

        processing_status VARCHAR(10) NOT NULL
            CONSTRAINT DF_webhook_events_processing_status DEFAULT 'RECEIVED',

        attempt_count INT NOT NULL
            CONSTRAINT DF_webhook_events_attempt_count DEFAULT 0,

        error_message NVARCHAR(1000) NULL,

        received_at DATETIME2(3) NOT NULL
            CONSTRAINT DF_webhook_events_received_at DEFAULT SYSUTCDATETIME(),

        processed_at DATETIME2(3) NULL,

        CONSTRAINT PK_webhook_events PRIMARY KEY (id),

        -- The same Razorpay event can be stored only once
        CONSTRAINT UQ_webhook_events_event_id UNIQUE (razorpay_event_id),

        CONSTRAINT CK_webhook_events_event_id
            CHECK (LEN(LTRIM(RTRIM(razorpay_event_id))) > 0),

        CONSTRAINT CK_webhook_events_event_type
            CHECK (LEN(LTRIM(RTRIM(event_type))) > 0),

        CONSTRAINT CK_webhook_events_payload
            CHECK (ISJSON(payload) = 1),

        CONSTRAINT CK_webhook_events_status
            CHECK (processing_status IN ('RECEIVED', 'PROCESSED', 'FAILED', 'IGNORED')),

        CONSTRAINT CK_webhook_events_attempt_count
            CHECK (attempt_count >= 0),

        -- Each status must carry exactly the fields that make sense for it
        CONSTRAINT CK_webhook_events_state
            CHECK (
                (processing_status = 'RECEIVED'
                    AND processed_at IS NULL
                    AND error_message IS NULL)
                OR
                (processing_status = 'PROCESSED'
                    AND processed_at IS NOT NULL
                    AND error_message IS NULL)
                OR
                (processing_status = 'FAILED'
                    AND LEN(LTRIM(RTRIM(ISNULL(error_message, '')))) > 0)
                OR
                (processing_status = 'IGNORED'
                    AND processed_at IS NOT NULL)
            ),

        -- A webhook with a bad signature must never be treated as processed
        CONSTRAINT CK_webhook_events_signature
            CHECK (signature_valid = 1 OR processing_status <> 'PROCESSED')
    );
END
GO

-- Retry job: "events still RECEIVED or FAILED, oldest first"
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_webhook_events_status_received'
      AND object_id = OBJECT_ID(N'dbo.webhook_events')
)
BEGIN
    CREATE INDEX IX_webhook_events_status_received
        ON dbo.webhook_events (processing_status, received_at);
END
GO

/* ---- Phase 4 change: title image for campaigns ---- */
IF COL_LENGTH('dbo.campaigns', 'image_url') IS NULL
    ALTER TABLE dbo.campaigns ADD image_url NVARCHAR(500) NULL;
GO