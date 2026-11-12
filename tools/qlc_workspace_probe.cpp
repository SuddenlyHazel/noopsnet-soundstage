// QLC+ engine-only workspace roundtrip. This does not launch QLC+ or load I/O
// plugins. Build against the matching upstream engine headers and library.
#include <QCoreApplication>
#include <QDir>
#include <QFile>
#include <QXmlStreamReader>
#include <QXmlStreamWriter>
#include <cstdlib>
#include <iostream>
#include <set>
#include <tuple>
#include "doc.h"
#include "fixture.h"
#include "fixturegroup.h"
#include "monitorproperties.h"
#include "qlcfixturedefcache.h"

static bool loadEngine(Doc &doc, QXmlStreamReader &reader) {
    while (!reader.atEnd()) {
        reader.readNext();
        if (reader.isStartElement() && reader.name() == QStringLiteral("Engine"))
            return doc.loadXML(reader,false) && !reader.hasError();
    }
    return false;
}
static bool loadDefinitions(Doc &doc,const QDir &dir) {
    for (auto name:{"NoopsNet-RGB.qxf","NoopsNet-LASER.qxf","NoopsNet-FOG.qxf","NoopsNet-PARTICLE.qxf"})
        if (!doc.fixtureDefCache()->loadQXF(dir.filePath(QString::fromLatin1(name)),true)) return false;
    return true;
}
int main(int argc,char **argv) {
    QCoreApplication app(argc,argv);
    if (argc!=4) return 2;
    QFile file(QString::fromLocal8Bit(argv[1]));
    if (!file.open(QIODevice::ReadOnly)) return 3;
    const QByteArray input=file.readAll();
    QDir definitions(QString::fromLocal8Bit(argv[2]));
    Doc first(nullptr,1);
    if (!loadDefinitions(first,definitions)) return 4;
    QXmlStreamReader reader(input);
    if (!loadEngine(first,reader)) return 5;
    if (!first.errorLog().isEmpty()) {std::cerr << first.errorLog().toStdString() << '\n'; return 17;}
    if (first.fixtures().size()!=std::atoi(argv[3])) return 6;
    int grouped=0;
    for (auto *group:first.fixtureGroups()) grouped+=group->headsMap().size();
    if (grouped!=first.fixtures().size()) return 7;
    auto *monitor=first.monitorProperties();
    if (monitor->fixtureItemsID().size()!=first.fixtures().size()) return 8;
    std::set<std::tuple<int,int,int>> positions;
    for (auto *fixture:first.fixtures()) {
        if (!fixture->fixtureDef() || !fixture->fixtureMode()) return 18;
        auto p=monitor->fixturePosition(fixture->id(),0,0);
        positions.emplace(int(p.x()),int(p.y()),int(p.z()));
    }
    if (positions.size()!=size_t(first.fixtures().size())) return 9;
    QByteArray saved;
    QXmlStreamWriter writer(&saved);
    if (!first.saveXML(&writer)) return 10;
    Doc second(nullptr,1);
    if (!loadDefinitions(second,definitions)) return 11;
    QXmlStreamReader savedReader(saved);
    if (!loadEngine(second,savedReader)) return 12;
    if (!second.errorLog().isEmpty()) {std::cerr << second.errorLog().toStdString() << '\n'; return 19;}
    if (second.fixtures().size()!=first.fixtures().size()
        ||second.fixtureGroups().size()!=first.fixtureGroups().size()) return 13;
    for (auto *fixture:first.fixtures()) {
        auto *copy=second.fixture(fixture->id());
        if (!copy || copy->name()!=fixture->name() || copy->universe()!=fixture->universe()
            ||copy->address()!=fixture->address() ||copy->channels()!=fixture->channels()) return 14;
        if (monitor->fixturePosition(fixture->id(),0,0)!=second.monitorProperties()->fixturePosition(fixture->id(),0,0)
            ||monitor->fixtureRotation(fixture->id(),0,0)!=second.monitorProperties()->fixtureRotation(fixture->id(),0,0)) return 15;
    }
    for (auto *group:first.fixtureGroups()) {
        auto *copy=second.fixtureGroup(group->id());
        if (!copy ||copy->name()!=group->name() ||copy->size()!=group->size()
            ||copy->headsMap()!=group->headsMap()) return 16;
    }
    std::cout << "QLC+ engine workspace roundtrip: " << first.fixtures().size() << " fixtures, "
              << first.fixtureGroups().size() << " groups, names/DMX/positions/rotations preserved\n";
    return 0;
}
