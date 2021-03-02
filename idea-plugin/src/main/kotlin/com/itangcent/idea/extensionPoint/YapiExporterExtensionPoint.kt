package com.itangcent.idea.extensionPoint

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.extensions.AbstractExtensionPointBean
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.impl.file.PsiDirectoryImpl
import com.intellij.psi.impl.source.PsiJavaFileImpl
import com.itangcent.idea.plugin.api.cache.DefaultFileApiCacheRepository
import com.itangcent.idea.plugin.api.cache.FileApiCacheRepository
import com.itangcent.idea.plugin.api.cache.ProjectCacheRepository
import com.itangcent.idea.plugin.api.export.*
import com.itangcent.idea.plugin.api.export.yapi.*
import com.itangcent.idea.plugin.config.RecommendConfigReader
import com.itangcent.idea.plugin.rule.SuvRuleParser
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.utils.ConfigurableLogger
import com.itangcent.idea.utils.CustomizedPsiClassHelper
import com.itangcent.idea.utils.RuleComputeListenerRegistry
import com.itangcent.intellij.actions.ActionEventDataContextAdaptor
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.rule.RuleComputeListener
import com.itangcent.intellij.config.rule.RuleParser
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.file.DefaultLocalFileRepository
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.intellij.jvm.JvmClassHelper
import com.itangcent.intellij.jvm.PsiClassHelper
import com.itangcent.intellij.jvm.standard.StandardJvmClassHelper
import com.itangcent.intellij.logger.ConsoleRunnerLogger
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.logger.NotificationHelper
import com.itangcent.suv.http.ConfigurableHttpClientProvider
import com.itangcent.suv.http.HttpClientProvider

class YapiExporterExtensionPoint : AbstractExtensionPointBean() {

    fun doExport(project: Project, path: String) {
        val actionContextBuilder = ActionContext.builder()
        this.init(actionContextBuilder, project)

        val actionContext = actionContextBuilder.build()
        actionContext.cache(CommonDataKeys.PSI_FILE.name, null)
        val vf = LocalFileSystem.getInstance().findFileByPath(path)
        val psiManagerImpl = PsiManagerImpl(project)
        actionContext.cache(CommonDataKeys.NAVIGATABLE.name, PsiDirectoryImpl(psiManagerImpl, vf!!))
        actionContext.init(this)

        if (actionContext.lock()) {
            actionContext.runAsync {
                actionContext.instance(YapiApiExporter::class).export(true)
            }
        } else {
            actionContext.runInWriteUI {
                NotificationHelper.instance().notify {
                    it.createNotification(
                            "Found unfinished task!",
                            NotificationType.ERROR
                    )
                }
            }
        }

        actionContext.waitCompleteAsync()
    }

    fun doExportByPsiFile(project: Project, psiFile : PsiFile) {
        val actionContextBuilder = ActionContext.builder()
        this.init(actionContextBuilder, project)

        val actionContext = actionContextBuilder.build()
        val psiJavaFileImpl = psiFile as PsiJavaFileImpl
        actionContext.cache(CommonDataKeys.PSI_FILE.name, psiJavaFileImpl);
        actionContext.init(this)

        if (actionContext.lock()) {
            actionContext.runAsync {
                actionContext.instance(YapiApiExporter::class).export(true)
            }
        } else {
            actionContext.runInWriteUI {
                NotificationHelper.instance().notify {
                    it.createNotification(
                        "Found unfinished task!",
                        NotificationType.ERROR
                    )
                }
            }
        }

        actionContext.waitCompleteAsync()

    }

    fun init(actionContextBuilder: ActionContext.ActionContextBuilder, project: Project) {
        actionContextBuilder.bindInstance(Project::class, project)
        actionContextBuilder.bind(DataContext::class) { it.with(ActionEventDataContextAdaptor::class).singleton() }
        actionContextBuilder.bindInstance("plugin.name", "easy_api")
        actionContextBuilder.bind(SettingBinder::class) { it.toInstance(ServiceManager.getService(SettingBinder::class.java)) }
        actionContextBuilder.bind(Logger::class) { it.with(ConfigurableLogger::class).singleton() }
        actionContextBuilder.bind(Logger::class, "delegate.logger") { it.with(ConsoleRunnerLogger::class).singleton() }
        actionContextBuilder.bind(LocalFileRepository::class) { it.with(DefaultLocalFileRepository::class).singleton() }
        actionContextBuilder.bind(HttpClientProvider::class) { it.with(ConfigurableHttpClientProvider::class).singleton() }
        actionContextBuilder.bind(LinkResolver::class) { it.with(YapiLinkResolver::class).singleton() }
        actionContextBuilder.bind(ConfigReader::class, "delegate_config_reader") { it.with(YapiConfigReader::class).singleton() }
        actionContextBuilder.bind(ConfigReader::class) { it.with(RecommendConfigReader::class).singleton() }
        actionContextBuilder.bind(YapiApiHelper::class) { it.with(YapiCachedApiHelper::class).singleton() }
        actionContextBuilder.bindInstance("AVAILABLE_CLASS_EXPORTER", arrayOf<Any>(YapiSpringRequestClassExporter::class, YapiMethodDocClassExporter::class))
        actionContextBuilder.bindInstance("file.save.default", "yapi.json")
        actionContextBuilder.bindInstance("file.save.last.location.key", "com.itangcent.yapi.export.path")
        actionContextBuilder.bind(PsiClassHelper::class) { it.with(YapiPsiClassHelper::class).singleton() }
        actionContextBuilder.bind(RuleParser::class) { it.with(SuvRuleParser::class).singleton() }
        actionContextBuilder.bind(RuleComputeListener::class) { it.with(RuleComputeListenerRegistry::class).singleton() }
        actionContextBuilder.bind(PsiClassHelper::class) { it.with(CustomizedPsiClassHelper::class).singleton() }
        actionContextBuilder.bind(ClassExporter::class) { it.with(DefaultMethodDocClassExporter::class).singleton() }
        actionContextBuilder.bind(FileApiCacheRepository::class) { it.with(DefaultFileApiCacheRepository::class).singleton() }
        actionContextBuilder.bind(LocalFileRepository::class, "projectCacheRepository") {
            it.with(ProjectCacheRepository::class).singleton()
        }
        actionContextBuilder.bind(JvmClassHelper::class) { it.with(StandardJvmClassHelper::class).singleton() }

    }
}