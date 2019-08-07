package com.github.goober.sonarqube.plugin.decorator.bitbucket;

import com.github.goober.sonarqube.plugin.decorator.PullRequestDecorator;
import com.github.goober.sonarqube.plugin.decorator.bitbucket.model.CreateAnnotationsRequest;
import com.github.goober.sonarqube.plugin.decorator.sonarqube.SonarQubeClient;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import java.io.IOException;

public class BitbucketPullRequestDecorator implements PullRequestDecorator {

    private static final Logger LOGGER = Loggers.get(BitbucketPullRequestDecorator.class);

    private final SonarQubeClient sonarqubeClient;
    private final BitbucketClient bitbucketClient;

    public BitbucketPullRequestDecorator(SonarQubeClient sonarqubeClient, BitbucketClient bitbucketClient) {
        this.sonarqubeClient = sonarqubeClient;
        this.bitbucketClient = bitbucketClient;
    }

    @Override
    public boolean isActivated() {
        return bitbucketClient.isConfigured();
    }

    @Override
    public void decorate(ProjectAnalysis analysis) {
        BitbucketPullRequestReport report = new BitbucketPullRequestReport(analysis);
        try {

            report.setReportDetails(sonarqubeClient.listMetrics().getMetrics())
                    .setReportData(sonarqubeClient.listMeasures(analysis.getProject().getKey(), report.getPullRequestId(),
                            "new_code_smells",
                            "new_bugs",
                            "new_vulnerabilities",
                            "new_coverage",
                            "new_duplicated_lines_density"))
                    .setAnnotations(sonarqubeClient.listOpenIssues(analysis.getProject().getKey(), report.getPullRequestId()).getIssues());

            bitbucketClient.createReport(report.getProject(),
                    report.getRepository(),
                    report.getRevision(),
                    report.toCreateReportRequest()
            );

            bitbucketClient.deleteAnnotations(report.getProject(), report.getRepository(), report.getRevision());

            bitbucketClient.createAnnotations(report.getProject(),
                    report.getRepository(),
                    report.getRevision(),
                    CreateAnnotationsRequest.builder()
                            .annotations(report.getAnnotations())
                            .build());

        } catch (IOException e) {
            LOGGER.error("Could not decorate pull request {}, in project {}", report.getPullRequestId(), analysis.getProject().getKey(), e);
        }
    }
}